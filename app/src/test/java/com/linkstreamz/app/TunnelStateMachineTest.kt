package com.linkstreamz.app

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TunnelStateMachineTest {

    private val endpoint = TunnelProxyEndpoint(
        host = "127.0.0.1",
        port = 45678,
        user = "u",
        password = "p",
    )

    private class FakeEngine(
        private val endpoint: TunnelProxyEndpoint,
    ) : TunnelEngine {
        @Volatile var startResult = true
        @Volatile var started = false
        @Volatile var lastMtu = -1
        @Volatile var probeResult = -1
        @Volatile var status: TunnelStatus? = null
        val startCalls = AtomicInteger(0)

        override fun start(config: WgConfig, mtu: Int): Boolean {
            startCalls.incrementAndGet()
            lastMtu = mtu
            started = startResult
            return startResult
        }

        override fun stop() {
            started = false
        }

        override val isUp: Boolean get() = started

        override fun proxy(): TunnelProxyEndpoint? = if (started) endpoint else null

        override fun status(): TunnelStatus? = status

        override fun probe(url: String, timeoutMs: Int): Int = probeResult

        override fun rebind(): Boolean = true
    }

    private lateinit var engine: FakeEngine

    @Before
    fun setUp() {
        engine = FakeEngine(endpoint)
        TunnelManager.setEngineForTest(engine)
        TunnelManager.egressDiagnostics = false
        TunnelManager.connectTimeoutMs = 5_000
        TunnelManager.probeRetryMs = 10
        TunnelManager.healthIntervalMs = 5_000
        TunnelManager.retryBaseMs = 5_000
        TunnelManager.maxRetryDelayMs = 10_000
        TunnelManager.probeTimeoutMs = 50
    }

    @After
    fun tearDown() {
        TunnelManager.stop()
        TunnelManager.setEngineForTest(FakeEngine(endpoint))
        TunnelManager.egressDiagnostics = true
        TunnelManager.connectTimeoutMs = 12_000
        TunnelManager.probeRetryMs = 500
        TunnelManager.healthIntervalMs = 30_000
        TunnelManager.retryBaseMs = 2_000
        TunnelManager.maxRetryDelayMs = 60_000
        TunnelManager.probeTimeoutMs = 5_000
    }

    private fun freshStatus(): TunnelStatus = TunnelStatus(
        up = true,
        handshakeSec = System.currentTimeMillis() / 1000,
        handshakeNsec = 0,
        txBytes = 100,
        rxBytes = 200,
        endpoint = "198.51.100.7:51820",
        listenPort = 51820,
    )

    private fun staleStatus(): TunnelStatus = freshStatus().copy(
        handshakeSec = System.currentTimeMillis() / 1000 - 10 * 60,
    )

    private fun config(): WgConfig = WgConfig(
        iface = WgInterfaceConfig(
            privateKey = "0".repeat(64),
            addresses = listOf("10.8.0.2/24"),
            dns = listOf("1.1.1.1"),
            mtu = 1420,
        ),
        peer = WgPeerConfig(
            publicKey = "1".repeat(64),
            endpoint = "198.51.100.7:51820",
            allowedIps = listOf("0.0.0.0/0"),
        ),
    )

    private fun awaitPhase(target: TunnelPhase, timeoutMs: Long = 3_000) {
        runBlocking {
            withTimeout(timeoutMs) {
                TunnelManager.phase.first { it == target }
            }
        }
    }

    @Test
    fun `does not go up until probe succeeds with a fresh handshake`() {
        engine.status = freshStatus()
        engine.probeResult = -1

        assertTrue(TunnelManager.start(config(), 1280))
        Thread.sleep(100)

        assertEquals(TunnelPhase.CONNECTING, TunnelManager.phase.value)
        assertNull(TunnelManager.currentProxy())

        engine.probeResult = 25
        awaitPhase(TunnelPhase.UP)
        assertNotNull(TunnelManager.currentProxy())
    }

    @Test
    fun `stale handshake keeps the tunnel down even when the probe works`() {
        engine.status = staleStatus()
        engine.probeResult = 25
        TunnelManager.connectTimeoutMs = 200
        TunnelManager.retryBaseMs = 10_000

        assertTrue(TunnelManager.start(config(), 1280))
        awaitPhase(TunnelPhase.DOWN)

        assertFalse(TunnelManager.isUp())
        assertNull(TunnelManager.currentProxy())
    }

    @Test
    fun `missing handshake keeps the tunnel down`() {
        engine.status = freshStatus().copy(handshakeSec = 0)
        engine.probeResult = 25
        TunnelManager.connectTimeoutMs = 200
        TunnelManager.retryBaseMs = 10_000

        assertTrue(TunnelManager.start(config(), 1280))
        awaitPhase(TunnelPhase.DOWN)

        assertNull(TunnelManager.currentProxy())
    }

    @Test
    fun `failed engine start stays down and never proxies`() {
        engine.startResult = false

        assertFalse(TunnelManager.start(config(), 1280))
        assertEquals(TunnelPhase.DOWN, TunnelManager.phase.value)
        assertNull(TunnelManager.currentProxy())
    }

    @Test
    fun `health failure takes the tunnel down and hides the proxy`() {
        engine.status = freshStatus()
        engine.probeResult = 25
        TunnelManager.healthIntervalMs = 30
        TunnelManager.retryBaseMs = 10_000

        TunnelManager.start(config(), 1280)
        awaitPhase(TunnelPhase.UP)
        assertNotNull(TunnelManager.currentProxy())

        engine.probeResult = -1
        awaitPhase(TunnelPhase.DOWN)
        assertNull(TunnelManager.currentProxy())
    }

    @Test
    fun `reconnects after a health failure and recovers`() {
        engine.status = freshStatus()
        engine.probeResult = 25
        TunnelManager.healthIntervalMs = 30
        TunnelManager.retryBaseMs = 200
        TunnelManager.maxRetryDelayMs = 200

        TunnelManager.start(config(), 1280)
        awaitPhase(TunnelPhase.UP)

        engine.probeResult = -1
        awaitPhase(TunnelPhase.DOWN)

        engine.status = freshStatus()
        engine.probeResult = 25
        awaitPhase(TunnelPhase.UP)
        assertNotNull(TunnelManager.currentProxy())
    }

    @Test
    fun `network change restarts with the new mtu and re-probes`() {
        engine.status = freshStatus()
        engine.probeResult = 25

        TunnelManager.start(config(), 1280)
        awaitPhase(TunnelPhase.UP)
        val before = engine.startCalls.get()

        TunnelManager.onNetworkChanged(1200, externalVpn = true)

        runBlocking {
            withTimeout(3_000) {
                while (engine.startCalls.get() <= before) delay(10)
            }
        }
        awaitPhase(TunnelPhase.UP)
        assertEquals(1200, engine.lastMtu)
        assertNotNull(TunnelManager.currentProxy())
    }

    @Test
    fun `stop from up hides the proxy`() {
        engine.status = freshStatus()
        engine.probeResult = 25

        TunnelManager.start(config(), 1280)
        awaitPhase(TunnelPhase.UP)

        TunnelManager.stop()
        assertEquals(TunnelPhase.DOWN, TunnelManager.phase.value)
        assertNull(TunnelManager.currentProxy())
    }

    @Test
    fun `awaitUp returns false before the gate opens`() {
        engine.status = freshStatus()
        engine.probeResult = -1
        TunnelManager.connectTimeoutMs = 500

        TunnelManager.start(config(), 1280)
        val result = runBlocking { TunnelManager.awaitUp(120) }
        assertFalse(result)
    }
}
