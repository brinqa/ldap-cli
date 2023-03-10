/*
 * Copyright 2023 Brinqa, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brinqa.ldap.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int
import org.forgerock.opendj.ldap.*
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl.DECODER
import org.forgerock.opendj.ldap.requests.Requests
import org.forgerock.opendj.ldap.responses.Result
import org.forgerock.opendj.ldap.responses.SearchResultEntry
import org.forgerock.opendj.ldap.responses.SearchResultReference
import org.forgerock.opendj.ldap.schema.AttributeType
import org.forgerock.opendj.ldap.schema.Schema
import org.glassfish.grizzly.nio.transport.TCPNIOTransport
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder
import java.io.IOException
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class App : CliktCommand() {

    override fun run() {
    }
}

class PrintSupportedCiphers : CliktCommand(name = "ciphers") {

    override fun run() {
        val ssf = SSLServerSocketFactory.getDefault() as SSLServerSocketFactory

        val defaultCiphers = ssf.defaultCipherSuites!!
        val availableCiphers = ssf.supportedCipherSuites!!

        val ciphers = TreeMap<String, Boolean>()
        for (i in availableCiphers.indices) {
            ciphers[availableCiphers[i]] = java.lang.Boolean.FALSE
        }

        for (i in defaultCiphers.indices) {
            ciphers[defaultCiphers[i]] = java.lang.Boolean.TRUE
        }
        println("Default\tCipher")
        ciphers.forEach { (cipher, enabled) ->
            val x = if (enabled) '*' else ' '
            print("$x\t$cipher")
        }
    }
}

class KeyStoreDebug : CliktCommand(name = "ssl-connect", help = "check the keystore") {

    private val host: String by option(help = "Hostname for the AD server").default("google.com")
    private val port: Int by option(help = "Port for the AD server").int().default(443)

    override fun run() {
        val sslSocketFactory = SSLSocketFactory.getDefault()!!
        val sslSocket = sslSocketFactory.createSocket(host, port) as SSLSocket

        val inputStream = sslSocket.inputStream
        val outputStream = sslSocket.outputStream

        outputStream.write(1)
        while (inputStream.available() > 0) {
            print(inputStream.read())
        }
        println("Secured connection performed successfully")
    }
}

class LDAPSchema : CliktCommand(name = "schema", help = "Print LDAP Schema") {

    private val host: String by option(help = "Hostname for the LDAP/AD server").prompt("Hostname")
    private val port: Int by option(help = "Port for the AD server").int().default(389)
    private val username: String by option(help = "Username for the connection").prompt("Username")
    private val password: String by option(help = "Password for the connection").prompt(text = "Password", hideInput = true)

    private val baseContext: String by option(help = "Base context for search.").prompt("Base Context")

    private val tls: String by option(help = "Enable TLS").default("false")
    private val ssl: String by option(help = "Enable SSL").default("false")

    private val protocol: String by option(help = "Protocol").default("TLSv1.2")

    override fun run() {
        val factory = buildFactory(host, port, protocol, ssl, tls)
        factory.connection.use { conn ->
            if (username.isNotBlank()) {
                conn.bind(username, password.toCharArray())
            }
            println("Schema")

            val dn = DN.valueOf(baseContext)
            val schema = Schema.readSchemaForEntry(conn, dn)
            schema.objectClasses.forEach { oc ->
                println("ObjectClass: $oc.nameOrOID")

                println("-Required Attributes:")
                oc.requiredAttributes.forEach(this::printAttributeType)

                println("-Optional Attributes:")
                oc.optionalAttributes.forEach(this::printAttributeType)

                println("-Declared Required Attributes:")
                oc.declaredRequiredAttributes.forEach(this::printAttributeType)

                println("-Declared Optional Attributes:")
                oc.declaredOptionalAttributes.forEach(this::printAttributeType)
            }
        }
    }

    private fun printAttributeType(attrType: AttributeType) {
        println("--Attribute")
        println("    OID: ${attrType.oid}")
        println("    Name(s): ${attrType.names}")
        println("    Syntax: ${attrType.syntax}")
        println("    Description: ${attrType.description}")
    }
}

class LDAPSearch : CliktCommand(name = "search", help = "Search LDAP Directory") {

    private val host: String by option(help = "Hostname for the LDAP/AD server").prompt("Hostname")
    private val port: Int by option(help = "Port for the AD server").int().default(389)
    private val username: String by option(help = "Username for the connection").prompt("Username")
    private val password: String by option(help = "Password for the connection").prompt(text = "Password", hideInput = true)

    private val tls: String by option(help = "Enable TLS").default("false")
    private val ssl: String by option(help = "Enable SSL").default("false")

    private val protocol: String by option(help = "Protocol").default("TLSv1.2")

    private val baseContext: String by option(help = "Base context for search.").prompt("Base Context")

    private val pageSize: Int by option(help = "Page size during the search").int().default(100)
    private val filter: String by option(help = "Filter for search.").default("")

    override fun run() {
        val factory = buildFactory(host, port, protocol, ssl, tls)
        factory.connection.use { conn ->
            if (username.isNotBlank()) {
                conn.bind(username, password.toCharArray())
            }

            println("searching context: [$baseContext], filter: [$filter]")

            var cookie = ByteString.empty()
            do {
                val request = Requests.newSearchRequest(baseContext, SearchScope.WHOLE_SUBTREE, filter)
                    .addControl(SimplePagedResultsControl.newControl(true, pageSize, cookie))

                val result = conn.search(request, object : SearchResultHandler {
                    override fun handleEntry(e: SearchResultEntry): Boolean {
                        println(e)
                        return true
                    }

                    override fun handleResult(r: Result) {
                        println(r)
                    }

                    override fun handleReference(p0: SearchResultReference?): Boolean {
                        return false
                    }

                    override fun handleErrorResult(e: ErrorResultException) {
                        e.printStackTrace()
                    }

                })
                val control: SimplePagedResultsControl =
                    result.getControl(DECODER, DecodeOptions())
                cookie = control.cookie
            } while (cookie.length() != 0)
        }
    }


}

fun buildFactory(host: String, port: Int, protocol: String, ssl: String, tls: String): LDAPConnectionFactory {
    return LDAPConnectionFactory(host, port, buildLDAPOptions(protocol, ssl, tls))
}

private fun buildLDAPOptions(protocol: String, ssl: String, tls: String): LDAPOptions {
    val lo = LDAPOptions()
    //lo.setProviderClassLoader(getClass().getClassLoader());
    lo.tcpnioTransport = buildTransportInstance()
    if (ssl.toBoolean() || tls.toBoolean()) {
        println("Using SSL: ${ssl.toBoolean()}")
        lo.sslContext = sslContext(protocol)
        println("Using TLS: ${tls.toBoolean()}")
        lo.setUseStartTLS(tls.toBoolean())
    }
    return lo
}

private fun buildTransportInstance(): TCPNIOTransport = try {
    val builder = TCPNIOTransportBuilder.newInstance()
    val transport = builder.build()
    transport.start()
    transport
} catch (e: IOException) {
    throw RuntimeException(e)
}

fun sslContext(protocol: String): SSLContext {
    val bld = SSLContextBuilder()
        .setProtocol(protocol)
        .setTrustManager(TrustManagers.trustAll())
    return bld.sslContext
}

fun main(args: Array<String>) = App()
    .subcommands(LDAPSearch())
    .subcommands(LDAPSchema())
    .subcommands(KeyStoreDebug())
    .subcommands(PrintSupportedCiphers())
    .main(args)
