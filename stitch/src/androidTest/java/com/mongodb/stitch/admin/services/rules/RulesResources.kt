package com.mongodb.stitch.admin.services.rules
import com.android.volley.Request
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import org.bson.Document

internal enum class TwilioActions {
    Send
}

internal enum class AwsSesActions {
    Send
}

internal enum class GcmActions {
    Send
}

internal sealed class RuleActionsCreator {
    class Http(vararg actions: Request.Method): RuleActionsCreator()
    class Twilio(vararg actions: TwilioActions): RuleActionsCreator()
    class AwsSes(vararg actions: AwsSesActions): RuleActionsCreator()
    class Gcm(vararg actions: GcmActions): RuleActionsCreator()
}

@JsonIgnoreProperties(ignoreUnknown = true)
class RuleResponse
