package com.boynux.daastani

import com.google.gson.JsonObject


class ActionMessage(val action: Action, val message: String, val metadate: JsonObject) {
    enum class Action {
        PLAY,
        REGISTER
    }
}