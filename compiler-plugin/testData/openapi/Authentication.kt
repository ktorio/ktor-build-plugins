// RUN_PIPELINE_TILL: BACKEND

package openapi

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

enum class UserRole {
    ADMIN,
    USER,
    GUEST
}

data class User(
    val id: String,
    val username: String,
    val roles: Set<UserRole>,
    val scopes: Set<String>
)

class RoleBasedPrincipal(
    val user: User
) {
    fun hasRole(role: UserRole): Boolean = user.roles.contains(role)
    fun hasScope(scope: String): Boolean = user.scopes.contains(scope)
    fun hasAnyRole(vararg roles: UserRole): Boolean = user.roles.any { it in roles }
}

fun Application.installAuthentication() {
    authentication {
        oauth("auth-oauth-google") {
            urlProvider = { "http://localhost:8080/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("GOOGLE_CLIENT_ID"),
                    clientSecret = System.getenv("GOOGLE_CLIENT_SECRET"),
                    defaultScopes = listOf(
                        "https://www.googleapis.com/auth/userinfo.profile",
                        "https://www.googleapis.com/auth/userinfo.email",
                        "openid"
                    )
                )
            }
            client = HttpClient(Apache)
        }
        oauth("auth-oauth-github") {
            urlProvider = { "http://localhost:8080/github-callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "github",
                    authorizeUrl = "https://github.com/login/oauth/authorize",
                    accessTokenUrl = "https://github.com/login/oauth/access_token",
                    requestMethod = HttpMethod.Post,
                    clientId = System.getenv("GITHUB_CLIENT_ID"),
                    clientSecret = System.getenv("GITHUB_CLIENT_SECRET"),
                    defaultScopes = listOf("user:email", "repo")
                )
            }
            client = HttpClient(Apache)
        }
        jwt("auth-jwt") {
            realm = "ktor sample app"
            verifier(
                JWT
                    .require(Algorithm.HMAC256("secret"))
                    .withAudience("jwt-audience")
                    .withIssuer("https://jwt-provider-domain/")
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains("jwt-audience")) {
                    val rolesClaim = credential.payload.getClaim("roles")
                    val scopesClaim = credential.payload.getClaim("scope")

                    val roles = rolesClaim.asList(String::class.java)
                        ?.mapNotNull { role -> runCatching { UserRole.valueOf(role) }.getOrNull() }
                        ?.toSet() ?: emptySet()

                    val scopes = scopesClaim.asString()
                        ?.split(" ")
                        ?.toSet() ?: emptySet()

                    val user = User(
                        id = credential.payload.subject,
                        username = credential.payload.getClaim("username").asString() ?: "unknown",
                        roles = roles,
                        scopes = scopes
                    )

                    RoleBasedPrincipal(user)
                } else null
            }
        }
        bearer("auth-api-key-header") {
            realm = "API Access"
            authenticate { tokenCredential ->
                if (tokenCredential.token == "valid-api-key") {
                    UserIdPrincipal("api-user")
                } else null
            }
        }
        basic("auth-api-key-basic") {
            realm = "API Access via Basic Auth"
            validate { credentials ->
                if (credentials.name == "apikey" && credentials.password == "valid-api-key") {
                    UserIdPrincipal("api-user")
                } else null
            }
        }
        basic("auth-cookie-fallback") {
            realm = "Cookie Authentication"
            validate { credentials ->
                if (credentials.name == "session" && credentials.password.isNotEmpty()) {
                    UserIdPrincipal("session-user")
                } else null
            }
        }

    }
}

fun Application.routingWithAuthentication() {
    routing {
        authenticate("auth-oauth-google") {
            get("/login") {
                call.respondRedirect("/callback")
            }

            get("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.authentication.principal()
                call.sessions.set(UserSession(principal?.accessToken.toString()))
                call.respondRedirect("/hello")
            }
        }
        authenticate(optional = true) {
            get("/hello") {
                call.respondText("Hello, world!", contentType = ContentType.Text.Plain)
            }
        }
        authenticate("auth-jwt", "auth-api-key-header", "auth-api-key-basic", optional = false) {
            /**
             * Protected endpoint with multiple auth methods
             * @response 200 Authentication successful
             * @response 401 Authentication failed
             */
            get("/protected-multi-auth") {
                call.respondText("Authentication successful")
            }
        }
        authenticate("auth-jwt") {
            /**
             * Admin-only endpoint
             * Requires ADMIN role
             * @response 200 Admin access granted
             * @response 401 Authentication failed
             * @response 403 Authorization failed, requires ADMIN role
             */
            get("/admin") {
                val principal = call.principal<RoleBasedPrincipal>()

                if (principal != null && principal.hasRole(UserRole.ADMIN)) {
                    call.respondText("Admin access granted")
                } else {
                    call.respond(HttpStatusCode.Forbidden, "Requires ADMIN role")
                }
            }

            /**
             * User or admin endpoint
             * Requires USER or ADMIN role
             * @response 200 Access granted
             * @response 401 Authentication failed
             * @response 403 Authorization failed, requires USER or ADMIN role
             */
            get("/user-content") {
                val principal = call.principal<RoleBasedPrincipal>()

                if (principal != null && principal.hasAnyRole(UserRole.USER, UserRole.ADMIN)) {
                    call.respondText("User or admin access granted")
                } else {
                    call.respond(HttpStatusCode.Forbidden, "Requires USER or ADMIN role")
                }
            }
        }
        authenticate("auth-jwt") {
            /**
             * Read-only API endpoint
             * Requires 'api:read' scope
             * @response 200 Read access granted
             * @response 401 Authentication failed
             * @response 403 Authorization failed, requires api:read scope
             */
            get("/api/data") {
                val principal = call.principal<RoleBasedPrincipal>()

                if (principal != null && principal.hasScope("api:read")) {
                    call.respondText("Read access granted")
                } else {
                    call.respond(HttpStatusCode.Forbidden, "Requires api:read scope")
                }
            }

            /**
             * Write API endpoint
             * Requires 'api:write' scope
             * @response 200 Write access granted
             * @response 401 Authentication failed
             * @response 403 Authorization failed, requires api:write scope
             */
            post("/api/data") {
                val principal = call.principal<RoleBasedPrincipal>()

                if (principal != null && principal.hasScope("api:write")) {
                    call.respondText("Write access granted")
                } else {
                    call.respond(HttpStatusCode.Forbidden, "Requires api:write scope")
                }
            }
        }
        route("/conditional") {
            /**
             * Public endpoint for GET, authenticated for POST
             * @response 200 Access granted
             */
            get {
                call.respondText("Public access")
            }

            authenticate("auth-api-key-header") {
                /**
                 * POST requires authentication
                 * @response 200 Authenticated access
                 * @response 401 Authentication failed
                 */
                post {
                    call.respondText("Authenticated access")
                }
            }
        }
        route("/combined-auth") {
            /**
             * Endpoint requiring both JWT and API key
             * @response 200 Combined auth successful
             * @response 401 Authentication failed
             */
            authenticate("auth-jwt") {
                authenticate("auth-api-key-header") {
                    get {
                        call.respondText("Combined authentication successful")
                    }
                }
            }
        }
    }
}

data class UserSession(val accessToken: String)