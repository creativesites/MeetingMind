package com.example.core.net

import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Connections that work on the networks people actually have.
 *
 * Many mobile and home networks hand out IPv6 addresses that go nowhere. OkHttp 4 tries a
 * server's addresses one after another, so it waits out the whole connect timeout on a dead IPv6
 * route before trying IPv4 — browsers race the two and never notice. Trying IPv4 first (IPv6 still
 * follows) turns "Gemini times out" into an instant connection on those networks.
 */
object Net {

    object Ipv4First : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            Dns.SYSTEM.lookup(hostname).sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    /** One pool for the whole app; callers derive from it with their own timeouts. */
    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Ipv4First)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun client(connectS: Long = 10, readS: Long = 60, writeS: Long = 60): OkHttpClient = base.newBuilder()
        .connectTimeout(connectS, TimeUnit.SECONDS)
        .readTimeout(readS, TimeUnit.SECONDS)
        .writeTimeout(writeS, TimeUnit.SECONDS)
        .build()
}
