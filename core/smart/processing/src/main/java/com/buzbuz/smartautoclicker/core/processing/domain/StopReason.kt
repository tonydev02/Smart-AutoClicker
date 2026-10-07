package com.buzbuz.smartautoclicker.core.processing.domain

/** Internal attribution for requests that stop detection. */
internal enum class StopReason {
    USER_OR_EXTERNAL_REQUEST,
    AUTO_STOP,
    ALL_EVENTS_DISABLED,
    PROJECTION_LOST,
    SCREEN_RECORD_STOP,
    SERVICE_STOP,
    UNKNOWN,
}
