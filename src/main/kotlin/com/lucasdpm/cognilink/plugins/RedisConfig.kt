package com.lucasdpm.cognilink.plugins

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.io.File
import java.util.*

object RedisConfig {
    private val properties = Properties().apply {
        val localPropertiesFile = File("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }

    private val redisHost = properties.getProperty("REDIS_HOST") ?: "localhost"
    private val redisPort = properties.getProperty("REDIS_PORT")?.toInt() ?: 6379
    private val redisPassword = properties.getProperty("REDIS_PASSWORD") ?: ""

    private val poolConfig = JedisPoolConfig().apply {
        maxTotal = 10
        maxIdle = 5
        minIdle = 1
    }

    val pool: JedisPool by lazy {
        if (redisPassword.isNotEmpty()) {
            JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword)
        } else {
            JedisPool(poolConfig, redisHost, redisPort)
        }
    }

    fun getSessionKey(sessionId: String) = "feynman:session:$sessionId"
}
