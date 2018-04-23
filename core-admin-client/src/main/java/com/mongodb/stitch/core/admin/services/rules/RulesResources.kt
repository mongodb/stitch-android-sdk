package com.mongodb.stitch.core.admin.services.rules

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.mongodb.stitch.core.internal.net.Method

enum class TwilioActions {
    Send
}

enum class AwsSesActions {
    Send
}

sealed class RuleActionsCreator {
    class Http(vararg actions: Method): RuleActionsCreator()
    class Twilio(vararg actions: TwilioActions): RuleActionsCreator()
    class AwsSes(vararg actions: AwsSesActions): RuleActionsCreator()
}

@JsonIgnoreProperties(ignoreUnknown = true)
class RuleResponse
