package com.mongodb.stitch.admin.services.rules
import com.android.volley.Request

import org.bson.Document

internal enum class TwilioActions {
    Send
}

internal enum class AwsSesActions {
    Send
}

internal sealed class RuleActionsCreator {
    class Http(vararg actions: Request.Method): RuleActionsCreator()
    class Twilio(vararg actions: TwilioActions): RuleActionsCreator()
    class AwsSes(vararg actions: AwsSesActions): RuleActionsCreator()
}

internal data class RuleCreator(val name: String,
                                val actions: RuleActionsCreator,
                                val `when`: Document)

class RuleResponse