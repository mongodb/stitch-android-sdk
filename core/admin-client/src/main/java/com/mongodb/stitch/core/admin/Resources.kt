package com.mongodb.stitch.core.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import com.mongodb.stitch.core.admin.apps.AppResponse
import com.mongodb.stitch.core.admin.authProviders.AuthProvidersResponse
import com.mongodb.stitch.core.admin.authProviders.ProviderConfigWrapper
import com.mongodb.stitch.core.admin.functions.FunctionCreator
import com.mongodb.stitch.core.admin.functions.FunctionResponse
import com.mongodb.stitch.core.admin.services.ServiceConfigWrapper
import com.mongodb.stitch.core.admin.services.ServiceResponse
import com.mongodb.stitch.core.admin.services.rules.RuleCreator
import com.mongodb.stitch.core.admin.services.rules.RuleResponse
import com.mongodb.stitch.core.admin.users.UserCreator
import com.mongodb.stitch.core.admin.users.UserResponse
import com.mongodb.stitch.core.internal.common.StitchObjectMapper
import com.mongodb.stitch.core.internal.net.Method
import com.mongodb.stitch.core.internal.net.StitchAuthRequest

val objMapper = StitchObjectMapper.getInstance().registerKotlinModule()
val writer: ObjectWriter = ObjectMapper().registerKotlinModule().writer()

// / Any endpoint that can be described with basic
// / CRUD operations
interface Resource {
    // / path to this endpoint
    val url: String
    // / stitch admin auth for making requests
    val adminAuth: StitchAdminAuth
}

// / Base implementation of Resource Protocol
abstract class BasicResource(
    val adminAuth: StitchAdminAuth,
    val url: String
)

// / Adds an endpoint method that GETs some list
interface Listable<T> : Resource

inline fun <reified T> Listable<T>.list(): List<T> {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.GET)
            .withPath(url)

    val response = adminAuth.doAuthenticatedRequest(reqBuilder.build())
    return objMapper.readValue<List<T>>(
            response.body,
            objMapper.typeFactory.constructCollectionType(List::class.java, T::class.java)
    )
}

// / Adds an endpoint method that GETs some id
interface Gettable<T> : Resource

inline fun <reified T> Gettable<T>.get(): T {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.GET)
            .withPath(url)

    val response = adminAuth.doAuthenticatedRequest(reqBuilder.build())
    return objMapper.readValue(
            response.body,
            T::class.java
    )
}

// / Adds an endpoint method that DELETEs some id
interface Removable : Resource

fun Removable.remove() {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.DELETE)
            .withPath(url)

    adminAuth.doAuthenticatedRequest(reqBuilder.build())
}

// / Adds an endpoint method that POSTs new data
interface Creatable<Creator, T> : Resource

inline fun <Creator, reified T> Creatable<Creator, T>.create(data: Creator): T {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.POST)
            .withPath(url)
            .withBody(writer.writeValueAsString(data).toByteArray())

    val response = adminAuth.doAuthenticatedRequest(reqBuilder.build())
    return objMapper.readValue(
            response.body,
            T::class.java
    )
}

// / Adds an endpoint method that PUTs some data
interface Updatable<T> : Resource

inline fun <reified T> Updatable<T>.update(data: T): T {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.PUT)
            .withPath(url)
            .withBody(writer.writeValueAsString(data).toByteArray())

    val response = adminAuth.doAuthenticatedRequest(reqBuilder.build())
    return objMapper.readValue(
            response.body,
            T::class.java
    )
}

// / Adds an endpoint that enables a given resource
interface Enablable : Resource

fun Enablable.enable() {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.PUT)
            .withPath("${this.url}/enable")

    adminAuth.doAuthenticatedRequest(reqBuilder.build())
}

// / Adds an endpoint that disables a given resource
interface Disablable : Resource

fun Disablable.disable() {
    val reqBuilder = StitchAuthRequest.Builder()
    reqBuilder
            .withMethod(Method.PUT)
            .withPath("${this.url}/disable")

    adminAuth.doAuthenticatedRequest(reqBuilder.build())
}

class Apps(adminAuth: StitchAdminAuth, url: String) :
        BasicResource(adminAuth, url), Listable<AppResponse> {
    class App(adminAuth: StitchAdminAuth, url: String) :
            BasicResource(adminAuth, url), Gettable<AppResponse>, Removable {
        // / Resource for listing the auth providers of an application
        class AuthProviders(adminAuth: StitchAdminAuth, url: String) :
                BasicResource(adminAuth, url), Listable<AuthProvidersResponse>, Creatable<ProviderConfigWrapper, AuthProvidersResponse> {
            // / Resource for a specific auth provider of an application
            class AuthProvider(adminAuth: StitchAdminAuth, url: String) :
                    BasicResource(adminAuth, url),
                    Gettable<AuthProvidersResponse>,
                    Updatable<AuthProvidersResponse>,
                    Removable,
                    Enablable,
                    Disablable
        }

        // / Resource for user registrations of an application
        class UserRegistrations(adminAuth: StitchAdminAuth, url: String) :
                BasicResource(adminAuth, url)

        // / Resource for a list of users of an application
        class Users(adminAuth: StitchAdminAuth, url: String) :
                BasicResource(adminAuth, url),
                Listable<UserResponse>,
                Creatable<UserCreator, UserResponse> {
            // / Resource for a single user of an application
            class User(adminAuth: StitchAdminAuth, url: String) :
                    BasicResource(adminAuth, url), Gettable<UserResponse>, Removable
        }

        class Functions(adminAuth: StitchAdminAuth, url: String) :
                BasicResource(adminAuth, url),
                Listable<FunctionResponse>,
                Creatable<FunctionCreator, FunctionResponse> {
            class Function(adminAuth: StitchAdminAuth, url: String) :
                    BasicResource(adminAuth, url),
                    Gettable<FunctionResponse>,
                    Updatable<FunctionCreator>,
                    Removable
        }

        // / Resource for listing services of an application
        class Services(adminAuth: StitchAdminAuth, url: String) :
                BasicResource(adminAuth, url),
                Listable<ServiceResponse>,
                Creatable<ServiceConfigWrapper, ServiceResponse> {

            // / Resource for a specific service of an application. Can fetch rules
            // / of the service
            class Service(adminAuth: StitchAdminAuth, url: String) :
                    BasicResource(adminAuth, url),
                    Gettable<ServiceResponse>, Removable {

                // / Resource for listing the rules of a service
                class Rules(adminAuth: StitchAdminAuth, url: String) :
                        BasicResource(adminAuth, url),
                        Listable<RuleResponse>,
                        Creatable<RuleCreator, RuleResponse> {
                    // / Resource for a specific rule of a service
                    class Rule(adminAuth: StitchAdminAuth, url: String) :
                            BasicResource(adminAuth, url),
                            Gettable<RuleResponse>, Removable
                }

                val rules by lazy { Rules(this.adminAuth, "$url/rules") }
            }
        }

        val authProviders by lazy { AuthProviders(this.adminAuth, "$url/auth_providers") }
        val functions by lazy { Functions(this.adminAuth, "$url/functions") }
        val services by lazy { Services(this.adminAuth, "$url/services") }
        val users by lazy { Users(this.adminAuth, "$url/users") }
        val userRegistrations by lazy { UserRegistrations(this.adminAuth, "$url/user_registrations") }
    }
}
