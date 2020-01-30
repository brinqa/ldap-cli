package brinqa.ldap.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import org.forgerock.opendj.ldap.ByteString
import org.forgerock.opendj.ldap.DecodeOptions
import org.forgerock.opendj.ldap.ErrorResultException
import org.forgerock.opendj.ldap.LDAPConnectionFactory
import org.forgerock.opendj.ldap.LDAPOptions
import org.forgerock.opendj.ldap.SSLContextBuilder
import org.forgerock.opendj.ldap.SearchResultHandler
import org.forgerock.opendj.ldap.SearchScope
import org.forgerock.opendj.ldap.TrustManagers
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl
import org.forgerock.opendj.ldap.requests.Requests
import org.forgerock.opendj.ldap.responses.Result
import org.forgerock.opendj.ldap.responses.SearchResultEntry
import org.forgerock.opendj.ldap.responses.SearchResultReference
import org.glassfish.grizzly.nio.transport.TCPNIOTransport
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder
import java.io.IOException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class App : CliktCommand() {
    override fun run() {
    }
}

class KeyStoreDebug : CliktCommand(name = "keystore", help = "check the keystore") {
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

class LDAPSearch : CliktCommand(name = "search", help = "Search LDAP Directory") {

    private val host: String by option(help = "Hostname for the AD server").prompt("Hostname")
    private val port: Int by option(help = "Port for the AD server").int().default(636)
    private val username: String by option(help = "Username for the connection").default("")
    private val password: String by option(help = "Password for the connection").default("")

    private val tls: String by option(help = "Enable TLS").default("false")
    private val ssl: String by option(help = "Enable SSL").default("false")

    private val protocol: String by option(help = "Protocol").default("TLSv1.2")

    private val baseContext: String by option(help = "Base context for search.").required()

    private val pageSize: Int by option(help = "Page size during the search").int().default(100)
    private val filter: String by option(help = "Filter for search.").default("")

    override fun run() {
        val factory = LDAPConnectionFactory(host, port, buildLDAPOptions(protocol))
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
                val control: SimplePagedResultsControl = result.getControl(SimplePagedResultsControl.DECODER, DecodeOptions())
                cookie = control.cookie
            } while (cookie.length() != 0)
        }
    }

    private fun buildLDAPOptions(protocol: String): LDAPOptions {
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

}

fun sslContext(protocol: String): SSLContext {
    val bld = SSLContextBuilder()
        .setProtocol(protocol)
        .setTrustManager(TrustManagers.trustAll())
    return bld.sslContext
}

fun main(args: Array<String>) = App()
    .subcommands(LDAPSearch())
    .subcommands(KeyStoreDebug())
    .main(args)
