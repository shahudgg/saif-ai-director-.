package com.example.flow

/**
 * The bridge between the full-screen Flow browser Activity and the AI Director screen that is still
 * open behind it. The plan is ALWAYS saved to disk first; this callback is only the live hand-off so
 * the timeline can be updated without the owner re-opening the chat.
 */
object FlowBridgeBus {
    /** (finished plan, apply to timeline now?) */
    @Volatile var onPlanReady: ((FlowPlan, Boolean) -> Unit)? = null

    /** How many pictures were imported by the browser while the director was closed. */
    @Volatile var onLibraryChanged: ((Int) -> Unit)? = null

    fun publishPlan(plan: FlowPlan, applyNow: Boolean) {
        onPlanReady?.invoke(plan, applyNow)
    }

    fun publishLibrary(count: Int) {
        onLibraryChanged?.invoke(count)
    }
}
