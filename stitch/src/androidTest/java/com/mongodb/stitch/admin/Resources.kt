package com.mongodb.stitch.admin

import com.android.volley.Request.Method.*
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.android.gms.tasks.Task
import com.mongodb.stitch.admin.apps.AppResponse
import com.mongodb.stitch.admin.authProviders.AuthProvidersResponse
import com.mongodb.stitch.admin.authProviders.ProviderConfigs
import com.mongodb.stitch.admin.services.ServiceConfigs
import com.mongodb.stitch.admin.services.ServiceResponse
import com.mongodb.stitch.admin.services.rules.RuleCreator
import com.mongodb.stitch.admin.services.rules.RuleResponse
import com.mongodb.stitch.android.StitchClient
import org.bson.Document
import org.bson.json.JsonMode
import org.bson.json.JsonWriterSettings
import org.bson.types.ObjectId
import java.io.IOException

/**
 * CustomObjectMapper is responsible for handling the serialization and deserialization of JSON
 * objects with special serialization support for [Document]s and [ObjectId]s
 */
internal object CustomObjectMapper {

    private var _singleton: ObjectMapper? = null

    fun createObjectMapper(): ObjectMapper? {
        if (_singleton != null) {
            return _singleton
        }
        _singleton = ObjectMapper().registerModule(SimpleModule("stitchModule")
                .addSerializer(Document::class.java, object : JsonSerializer<Document>() {
                    @Throws(IOException::class)
                    override fun serialize(
                            value: Document,
                            jsonGenerator: JsonGenerator,
                            provider: SerializerProvider
                    ) {
                        val writerSettings = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()
                        jsonGenerator.writeRawValue(value.toJson(writerSettings))
                    }
                })
                .addSerializer(ObjectId::class.java, object : JsonSerializer<ObjectId>() {
                    @Throws(IOException::class)
                    override fun serialize(
                            value: ObjectId,
                            jsonGenerator: JsonGenerator,
                            provider: SerializerProvider
                    ) {
                        jsonGenerator.writeString(value.toString())
                    }
                }))
        return _singleton
    }
}

val objMapper = CustomObjectMapper.createObjectMapper()!!

/// Any endpoint that can be described with basic
/// CRUD operations
internal interface Resource {
    /// absolute url to this endpoint
    val url: String
    /// stitch http client for making requests
    val httpClient: StitchClient
}

/// Base implementation of Resource Protocol
internal abstract class BasicResource(val httpClient: StitchClient,
                                      val url: String)

/// Adds an endpoint method that GETs some list
internal interface Listable<T>: Resource
internal inline fun <reified T> Listable<T>.list(): Task<List<T>> {
    return this.httpClient.executeRequest(
            GET,
            this.url
    ).continueWith { task: Task<String> ->
        if (!task.isSuccessful) {
            throw task.exception!!
        }

        objMapper.readValue<List<T>>(
                task.result,
                objMapper.typeFactory.constructCollectionType(List::class.java, T::class.java)
        )
    }
}

/// Adds an endpoint method that GETs some id
internal interface Gettable<T>: Resource
internal inline fun <reified T> Gettable<T>.get(): Task<T> {
    return this.httpClient.executeRequest(GET, this.url).continueWith { task: Task<String> ->
        if (!task.isSuccessful) {
            throw task.exception!!
        }

        objMapper.readValue(
                task.result,
                T::class.java
        )
    }
}

/// Adds an endpoint method that DELETEs some id
internal interface Removable: Resource
internal fun Removable.remove(): Task<Unit> {
    return this.httpClient.executeRequest(DELETE, this.url).continueWith {  }
}


/// Adds an endpoint method that POSTs new data
internal interface Creatable<Creator, T>: Resource
internal inline fun <Creator, reified T> Creatable<Creator, T>.create(data: Creator): Task<T> {
    return this.httpClient.executeRequest(
            POST,
            this.url,
            objMapper.writeValueAsString(data)
    ).continueWith { task ->
        if (!task.isSuccessful) {
            throw task.exception!!
        }

        objMapper.readValue(
                task.result,
                T::class.java
        )
    }
}

/// Adds an endpoint method that PUTs some data
internal interface Updatable<T>: Resource
internal inline fun <reified T> Updatable<T>.update(data: T): Task<T> {
    return this.httpClient.executeRequest(
            PUT,
            this.url,
            objMapper.writeValueAsString(data)
    ).continueWith { task ->
        if (!task.isSuccessful) {
            throw task.exception!!
        }

        objMapper.readValue(
                task.result,
                T::class.java
        )
    }
}

/// Adds an endpoint that enables a given resource
internal interface Enablable: Resource
internal fun Enablable.enable(): Task<Unit> {
    return this.httpClient.executeRequest(PUT, "${this.url}/enable").continueWith {  }
}

/// Adds an endpoint that disables a given resource
internal interface Disablable: Resource
internal fun Disablable.disable(): Task<Unit> {
    return this.httpClient.executeRequest(PUT, "${this.url}/disable").continueWith {  }
}

internal class Apps(httpClient: StitchClient, url: String):
        BasicResource(httpClient, url), Listable<AppResponse> {
    internal class App(httpClient: StitchClient, url: String):
            BasicResource(httpClient, url), Gettable<AppResponse>, Removable {
        /// Resource for listing the auth providers of an application
        internal class AuthProviders(httpClient: StitchClient, url: String):
                BasicResource(httpClient, url), Listable<AuthProvidersResponse>, Creatable<ProviderConfigs, AuthProvidersResponse> {
            /// Resource for a specific auth provider of an application
            internal class AuthProvider(httpClient: StitchClient, url: String):
                    BasicResource(httpClient, url),
                    Gettable<AuthProvidersResponse>,
                    Updatable<AuthProvidersResponse>,
                    Removable,
                    Enablable,
                    Disablable
        }

        /// Resource for listing services of an application
        internal class Services(httpClient: StitchClient, url: String):
                BasicResource(httpClient, url),
                Listable<ServiceResponse>,
                Creatable<ServiceConfigs, ServiceResponse> {

            /// Resource for a specific service of an application. Can fetch rules
            /// of the service
            internal class Service(httpClient: StitchClient, url: String):
                    BasicResource(httpClient, url),
                    Gettable<ServiceResponse>, Removable {

                /// Resource for listing the rules of a service
                internal class Rules(httpClient: StitchClient, url: String):
                        BasicResource(httpClient, url),
                        Listable<RuleResponse>,
                        Creatable<RuleCreator, RuleResponse> {
                    /// Resource for a specific rule of a service
                    internal class Rule(httpClient: StitchClient, url: String):
                            BasicResource(httpClient, url),
                            Gettable<RuleResponse>, Removable
                }

                val rules by lazy { Rules(this.httpClient, "$url/rules") }
            }
        }

        val authProviders by lazy { AuthProviders(this.httpClient, "$url/auth_providers") }
        val services by lazy { Services(this.httpClient, "$url/services") }

    }
}
