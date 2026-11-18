// Package linktunnel runs a WireGuard tunnel entirely inside the app process.
//
// It uses wireguard-go's userspace network stack (gvisor netstack) instead of a
// TUN device / VpnService. Nothing is installed system-wide: no other app, no
// other VPN and no status-bar key icon is affected.
//
// A loopback-only, authenticated HTTP proxy is exposed. The app points its own
// HTTP client at that proxy; the proxy dials upstream through the netstack so
// host names are resolved inside the tunnel (no DNS leaks).
package linktunnel

import (
	"bufio"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"strconv"
	"strings"
	"sync"
	"time"

	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/tun/netstack"
)

type tunnel struct {
	dev      *device.Device
	net      *netstack.Net
	listener net.Listener
	cancel   context.CancelFunc

	host string
	port int
	user string
	pass string

	// conns is used to tear down hijacked CONNECT sessions on Stop.
	conns sync.Map
}

var (
	mu      sync.Mutex
	current *tunnel
)

// Version returns the library version string (no secrets).
func Version() string { return "1" }

// IsUp reports whether a tunnel is currently running.
func IsUp() bool {
	mu.Lock()
	defer mu.Unlock()
	return current != nil
}

// ProxyHost returns the loopback address the local proxy listens on.
func ProxyHost() string {
	mu.Lock()
	defer mu.Unlock()
	if current == nil {
		return ""
	}
	return current.host
}

// ProxyPort returns the local proxy port, or 0 when down.
func ProxyPort() int {
	mu.Lock()
	defer mu.Unlock()
	if current == nil {
		return 0
	}
	return current.port
}

// ProxyUser returns the per-session proxy username.
func ProxyUser() string {
	mu.Lock()
	defer mu.Unlock()
	if current == nil {
		return ""
	}
	return current.user
}

// ProxyPassword returns the per-session proxy password.
func ProxyPassword() string {
	mu.Lock()
	defer mu.Unlock()
	if current == nil {
		return ""
	}
	return current.pass
}

type statusInfo struct {
	Up            bool   `json:"up"`
	HandshakeSec  int64  `json:"handshakeSec"`
	HandshakeNsec int64  `json:"handshakeNsec"`
	TxBytes       int64  `json:"txBytes"`
	RxBytes       int64  `json:"rxBytes"`
	Endpoint      string `json:"endpoint"`
	ListenPort    int64  `json:"listenPort"`
}

// Status returns a JSON snapshot of the running device's first peer.
// It never contains keys or the config. When down it returns {"up":false}.
func Status() string {
	mu.Lock()
	t := current
	if t == nil {
		mu.Unlock()
		b, _ := json.Marshal(statusInfo{})
		return string(b)
	}
	raw, err := t.dev.IpcGet()
	mu.Unlock()

	out := statusInfo{Up: true}
	if err != nil {
		b, _ := json.Marshal(out)
		return string(b)
	}
	parseStatus(raw, &out)
	b, _ := json.Marshal(out)
	return string(b)
}

func parseStatus(raw string, out *statusInfo) {
	seenPeer := false
	for _, line := range strings.Split(raw, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		eq := strings.IndexByte(line, '=')
		if eq <= 0 {
			continue
		}
		key := line[:eq]
		value := line[eq+1:]
		switch key {
		case "listen_port":
			if n, err := strconv.ParseInt(value, 10, 64); err == nil {
				out.ListenPort = n
			}
		case "public_key":
			// IpcGet emits the interface keys first; only inspect the first peer.
			if seenPeer {
				return
			}
			seenPeer = true
		case "endpoint":
			if seenPeer && out.Endpoint == "" {
				out.Endpoint = value
			}
		case "last_handshake_time_sec":
			if seenPeer {
				if n, err := strconv.ParseInt(value, 10, 64); err == nil {
					out.HandshakeSec = n
				}
			}
		case "last_handshake_time_nsec":
			if seenPeer {
				if n, err := strconv.ParseInt(value, 10, 64); err == nil {
					out.HandshakeNsec = n
				}
			}
		case "tx_bytes":
			if seenPeer {
				if n, err := strconv.ParseInt(value, 10, 64); err == nil {
					out.TxBytes = n
				}
			}
		case "rx_bytes":
			if seenPeer {
				if n, err := strconv.ParseInt(value, 10, 64); err == nil {
					out.RxBytes = n
				}
			}
		}
	}
}

// Rebind asks wireguard-go to re-open its UDP sockets on the current network.
// The caller should also restart the device when the endpoint IP or MTU
// changed, since those are applied at Start time.
func Rebind() error {
	mu.Lock()
	defer mu.Unlock()
	if current == nil {
		return errors.New("not running")
	}
	return current.dev.BindUpdate()
}

func (t *tunnel) httpClient(timeout time.Duration) *http.Client {
	tr := &http.Transport{
		Proxy: nil,
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			return t.net.DialContext(ctx, "tcp", addr)
		},
		ForceAttemptHTTP2:  false,
		DisableKeepAlives:  true,
		DisableCompression: true,
	}
	return &http.Client{Transport: tr, Timeout: timeout}
}

// Probe performs a GET through the netstack and returns the round-trip latency
// in milliseconds, or -1 on any failure. A successful probe implies a working
// WireGuard handshake and an in-tunnel DNS/TCP/TLS path.
func Probe(url string, timeoutMs int) int {
	mu.Lock()
	t := current
	mu.Unlock()
	if t == nil {
		return -1
	}
	if timeoutMs <= 0 {
		timeoutMs = 5000
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return -1
	}
	req.Header.Set("User-Agent", "linkstreamz-probe")

	start := time.Now()
	resp, err := t.httpClient(timeout).Do(req)
	if err != nil {
		return -1
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 64<<10))
	if resp.StatusCode < 200 || resp.StatusCode >= 400 {
		return -1
	}
	return int(time.Since(start) / time.Millisecond)
}

// HTTPGet returns the response body (up to 64 KiB) fetched through the tunnel,
// or an empty string on failure. Used only for egress diagnostics.
func HTTPGet(url string, timeoutMs int) string {
	mu.Lock()
	t := current
	mu.Unlock()
	if t == nil {
		return ""
	}
	if timeoutMs <= 0 {
		timeoutMs = 5000
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return ""
	}
	req.Header.Set("User-Agent", "linkstreamz-probe")

	resp, err := t.httpClient(timeout).Do(req)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return ""
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(body))
}

// Start creates the userspace WireGuard device and the loopback proxy.
//
// ipcConfig is the wireguard-go IPC text produced by the Kotlin parser, with
// all keys already converted to hex and the endpoint already resolved to an IP.
// addressesCsv / dnsCsv are comma separated bare IPs (no CIDR prefix).
func Start(ipcConfig string, addressesCsv string, dnsCsv string, mtu int) error {
	mu.Lock()
	defer mu.Unlock()
	stopLocked()

	if strings.TrimSpace(ipcConfig) == "" {
		return errors.New("empty config")
	}

	addrs, err := parseAddrList(addressesCsv)
	if err != nil {
		return err
	}
	if len(addrs) == 0 {
		return errors.New("no interface address")
	}
	dns, err := parseAddrList(dnsCsv)
	if err != nil {
		return err
	}
	if len(dns) == 0 {
		// Keep in-tunnel name resolution working even if DNS was omitted.
		dns = []netip.Addr{netip.MustParseAddr("1.1.1.1")}
	}
	if mtu <= 0 || mtu > 65535 {
		mtu = 1420
	}

	tunDev, tnet, err := netstack.CreateNetTUN(addrs, dns, mtu)
	if err != nil {
		return fmt.Errorf("netstack: %w", err)
	}

	dev := device.NewDevice(tunDev, conn.NewDefaultBind(), device.NewLogger(device.LogLevelSilent, "linktunnel"))
	if err := dev.IpcSet(ipcConfig); err != nil {
		dev.Close()
		return fmt.Errorf("ipc: %w", err)
	}
	if err := dev.Up(); err != nil {
		dev.Close()
		return fmt.Errorf("up: %w", err)
	}

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		dev.Close()
		return fmt.Errorf("proxy listen: %w", err)
	}
	addr, ok := ln.Addr().(*net.TCPAddr)
	if !ok {
		ln.Close()
		dev.Close()
		return errors.New("proxy listen: bad addr")
	}

	user, err := randomHex(12)
	if err != nil {
		ln.Close()
		dev.Close()
		return err
	}
	pass, err := randomHex(24)
	if err != nil {
		ln.Close()
		dev.Close()
		return err
	}

	ctx, cancel := context.WithCancel(context.Background())
	t := &tunnel{
		dev:      dev,
		net:      tnet,
		listener: ln,
		cancel:   cancel,
		host:     "127.0.0.1",
		port:     addr.Port,
		user:     user,
		pass:     pass,
	}
	current = t
	go t.serve(ctx)
	return nil
}

// Stop tears the tunnel down. Safe to call when already down.
func Stop() {
	mu.Lock()
	defer mu.Unlock()
	stopLocked()
}

func stopLocked() {
	t := current
	if t == nil {
		return
	}
	current = nil
	t.cancel()
	_ = t.listener.Close()
	t.conns.Range(func(k, _ any) bool {
		if c, ok := k.(net.Conn); ok {
			_ = c.Close()
		}
		return true
	})
	t.dev.Close()
}

func parseAddrList(csv string) ([]netip.Addr, error) {
	out := make([]netip.Addr, 0, 4)
	for _, part := range strings.Split(csv, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		// Accept both bare addresses and CIDR form.
		if strings.Contains(part, "/") {
			p, err := netip.ParsePrefix(part)
			if err != nil {
				return nil, fmt.Errorf("address %q: %w", part, err)
			}
			out = append(out, p.Addr())
		} else {
			a, err := netip.ParseAddr(part)
			if err != nil {
				return nil, fmt.Errorf("address %q: %w", part, err)
			}
			out = append(out, a)
		}
	}
	return out, nil
}

func randomHex(n int) (string, error) {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func (t *tunnel) serve(ctx context.Context) {
	for {
		c, err := t.listener.Accept()
		if err != nil {
			return
		}
		go t.handle(ctx, c)
	}
}

func (t *tunnel) authorized(r *http.Request) bool {
	h := r.Header.Get("Proxy-Authorization")
	const prefix = "Basic "
	if len(h) <= len(prefix) || !strings.EqualFold(h[:len(prefix)], prefix) {
		return false
	}
	raw, err := base64.StdEncoding.DecodeString(strings.TrimSpace(h[len(prefix):]))
	if err != nil {
		return false
	}
	want := t.user + ":" + t.pass
	return subtle.ConstantTimeCompare(raw, []byte(want)) == 1
}

func writeProxyAuthRequired(c net.Conn) {
	_, _ = io.WriteString(c, "HTTP/1.1 407 Proxy Authentication Required\r\n"+
		"Proxy-Authenticate: Basic realm=\"linkstreamz\"\r\n"+
		"Content-Length: 0\r\nConnection: close\r\n\r\n")
}

func writeBadGateway(c net.Conn) {
	_, _ = io.WriteString(c, "HTTP/1.1 502 Bad Gateway\r\n"+
		"Content-Length: 0\r\nConnection: close\r\n\r\n")
}

func (t *tunnel) handle(ctx context.Context, c net.Conn) {
	defer c.Close()
	_ = c.SetReadDeadline(time.Now().Add(30 * time.Second))

	br := bufio.NewReader(c)
	req, err := http.ReadRequest(br)
	if err != nil {
		return
	}
	_ = c.SetReadDeadline(time.Time{})

	if !t.authorized(req) {
		writeProxyAuthRequired(c)
		return
	}

	if req.Method == http.MethodConnect {
		t.handleConnect(ctx, c, br, req)
		return
	}
	t.handleForward(ctx, c, req)
}

func (t *tunnel) handleConnect(ctx context.Context, c net.Conn, br *bufio.Reader, req *http.Request) {
	target := req.Host
	if target == "" {
		target = req.URL.Host
	}
	if target == "" {
		writeBadGateway(c)
		return
	}
	if _, _, err := net.SplitHostPort(target); err != nil {
		target = net.JoinHostPort(target, "443")
	}

	dialCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	upstream, err := t.net.DialContext(dialCtx, "tcp", target)
	if err != nil {
		writeBadGateway(c)
		return
	}
	defer upstream.Close()
	t.conns.Store(c, struct{}{})
	t.conns.Store(upstream, struct{}{})
	defer t.conns.Delete(c)
	defer t.conns.Delete(upstream)

	if _, err := io.WriteString(c, "HTTP/1.1 200 Connection Established\r\n\r\n"); err != nil {
		return
	}

	done := make(chan struct{}, 2)
	go func() {
		_, _ = io.Copy(upstream, br)
		if tc, ok := upstream.(interface{ CloseWrite() error }); ok {
			_ = tc.CloseWrite()
		} else {
			_ = upstream.Close()
		}
		done <- struct{}{}
	}()
	go func() {
		_, _ = io.Copy(c, upstream)
		done <- struct{}{}
	}()
	<-done
	<-done
}

func (t *tunnel) handleForward(ctx context.Context, c net.Conn, req *http.Request) {
	host := req.URL.Host
	if host == "" {
		host = req.Host
	}
	if host == "" {
		writeBadGateway(c)
		return
	}
	if _, _, err := net.SplitHostPort(host); err != nil {
		host = net.JoinHostPort(host, "80")
	}

	dialCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	upstream, err := t.net.DialContext(dialCtx, "tcp", host)
	if err != nil {
		writeBadGateway(c)
		return
	}
	defer upstream.Close()
	t.conns.Store(c, struct{}{})
	t.conns.Store(upstream, struct{}{})
	defer t.conns.Delete(c)
	defer t.conns.Delete(upstream)

	req.RequestURI = ""
	req.Header.Del("Proxy-Authorization")
	req.Header.Del("Proxy-Connection")
	req.Header.Set("Connection", "close")
	req.Close = true

	if err := req.Write(upstream); err != nil {
		return
	}

	resp, err := http.ReadResponse(bufio.NewReader(upstream), req)
	if err != nil {
		writeBadGateway(c)
		return
	}
	defer resp.Body.Close()
	resp.Header.Del("Proxy-Authenticate")
	resp.Close = true
	resp.Header.Set("Connection", "close")
	_ = resp.Write(c)
}

// Ensure strconv import is retained for future tuning without breaking builds.
