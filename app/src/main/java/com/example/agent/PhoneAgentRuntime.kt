package com.example.agent

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue

object PhoneAgentRuntime {
    private const val TAG = "SAIF_AGENT"

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val uiMutex = Mutex()
    private val taskQueue = ConcurrentLinkedQueue<PhoneAgentTask>()
    private var currentExecutionJob: Job? = null
    private var currentTask: PhoneAgentTask? = null

    private val _status = MutableStateFlow(AgentStatus())
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        AgentLogger.init(context)
        PlaybookStore.init(context)
        MacroCache.init(context)
    }

    fun submit(goal: String, listener: AgentListener? = null, priority: Boolean = false): String {
        val taskId = "task_${System.currentTimeMillis()}"
        val task = PhoneAgentTask(id = taskId, goal = goal, listener = listener, priority = priority)

        if (priority) {
            // Cancel current and place at head
            currentExecutionJob?.cancel()
            val remaining = taskQueue.toList()
            taskQueue.clear()
            taskQueue.add(task)
            taskQueue.addAll(remaining)
        } else {
            taskQueue.add(task)
        }

        _status.value = _status.value.copy(
            queueSize = taskQueue.size
        )

        dispatchNext()
        return taskId
    }

    private fun dispatchNext() {
        runtimeScope.launch {
            if (!uiMutex.tryLock()) {
                // Another UI task is running, queued task will be picked up upon completion
                return@launch
            }

            try {
                while (true) {
                    val nextTask = taskQueue.poll() ?: break
                    currentTask = nextTask

                    _status.value = AgentStatus(
                        state = AgentStatusState.RUNNING,
                        currentGoal = nextTask.goal,
                        step = 0,
                        maxSteps = AgentConfig.MAX_STEPS,
                        lastMilestone = "Starting...",
                        queueSize = taskQueue.size
                    )

                    val ctx = appContext ?: continue
                    currentExecutionJob = launch {
                        PhoneAgent.run(nextTask, ctx)
                    }

                    currentExecutionJob?.join()
                    currentTask = null
                }
            } finally {
                _status.value = AgentStatus(state = AgentStatusState.IDLE, queueSize = taskQueue.size)
                uiMutex.unlock()
            }
        }
    }

    fun control(cmd: String): String {
        val clean = cmd.trim().lowercase()
        Log.i(TAG, "Runtime control received: $clean")

        return when {
            clean.contains("stop") || clean.contains("ruko") || clean.contains("cancel") || clean.contains("bas") -> {
                cancelCurrent()
                "Theek hai, task rok diya gaya hai."
            }
            clean.contains("cancel_all") || clean.contains("sab band") || clean.contains("sab cancel") -> {
                cancelAll()
                "Sabhi phone tasks cancel kar diye gaye hain."
            }
            clean.contains("status") || clean.contains("kya kar rahe ho") -> {
                val cur = currentTask
                if (cur != null) {
                    "Abhi '${cur.goal}' par kaam chal raha hai."
                } else {
                    "Abhi phone par koi task nahi chal raha hai."
                }
            }
            else -> {
                cancelCurrent()
                "Task cancel kar diya gaya hai."
            }
        }
    }

    fun cancelCurrent() {
        currentExecutionJob?.cancel()
        UserDialogBridge.cancel()
        _status.value = _status.value.copy(state = AgentStatusState.IDLE)
    }

    fun cancelAll() {
        taskQueue.clear()
        cancelCurrent()
    }
}
