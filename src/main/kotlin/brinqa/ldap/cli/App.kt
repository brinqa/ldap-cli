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
import org.forgerock.opendj.ldap.SearchResultHandler
import org.forgerock.opendj.ldap.SearchScope
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl
import org.forgerock.opendj.ldap.requests.Requests
import org.forgerock.opendj.ldap.responses.Result
import org.forgerock.opendj.ldap.responses.SearchResultEntry
import org.forgerock.opendj.ldap.responses.SearchResultReference

class App : CliktCommand() {
    override fun run() {
    }
}

class LDAPSearch : CliktCommand(name = "search", help = "Search LDAP Directory") {
    private val host: String by option(help = "Hostname for the AD server").prompt("Hostname")
    private val port: Int by option(help = "Port for the AD server").int().default(636)
    private val username: String by option(help = "Username for the connection").default("")
    private val password: String by option(help = "Password for the connection").default("")

    private val baseContext: String by option(help = "Base context for search.").required()

    private val pageSize: Int by option(help = "Page size during the search").int().default(100)
    private val filter: String by option(help = "Filter for search.").default("")


    override fun run() {
        val factory = LDAPConnectionFactory(host, port)
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
}

fun main(args: Array<String>) = App()
    .subcommands(LDAPSearch())
    .main(args)
