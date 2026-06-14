package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

/**
 * `flowbus-core` 提供的默认单例总线入口。
 *
 * 适合希望直接开箱即用，而不想自行管理 [FlowBus] 实例的场景。
 * 如果你需要多实例隔离、依赖注入或更细粒度的生命周期控制，仍然建议直接使用 [FlowBus]。
 *
 * 额外提醒：
 * - [configure] / [install] 必须在首次真正使用默认总线前完成
 * - 只要调用过 [raw]、`post(...)`、`flow(...)`、[scoped]、[openScope] 等入口，就算已经完成初始化
 * - [inspect] / [inspector] 只是只读诊断入口，不会触发默认总线初始化
 */
object DefaultFlowBus {
    @Volatile
    private var installedBus: FlowBus? = null

    /**
     * 在首次使用前，用固定配置安装默认总线。
     *
     * 如果默认总线已经被首次使用，会抛出 [IllegalStateException]。
     */
    fun configure(config: FlowBusConfig) {
        install(FlowBus(config))
    }

    /**
     * 在首次使用前，安装外部创建好的默认总线实例。
     *
     * 如果默认总线已经被首次使用，会抛出 [IllegalStateException]。
     */
    fun install(flowBus: FlowBus) {
        synchronized(this) {
            check(installedBus == null) {
                "DefaultFlowBus is already initialized. Configure or install it before first use."
            }
            installedBus = flowBus
        }
    }

    /**
     * 返回底层 [FlowBus]，便于在需要时回退到完整 API。
     *
     * 如果默认总线尚未初始化，这里会按默认配置创建一个实例。
     */
    fun raw(): FlowBus = getOrCreateBus()

    /**
     * 返回默认总线的只读诊断入口。
     *
     * 读取诊断入口不会触发默认总线初始化。
     */
    fun inspector(): FlowBusInspector = FlowBusInspector { inspect() }

    /**
     * 返回默认总线的只读诊断快照。
     *
     * 默认总线尚未初始化时，这里只返回默认配置摘要和空事件列表，不会影响后续 [configure] / [install]。
     */
    fun inspect(): FlowBusSnapshot = installedBus?.inspect() ?: emptySnapshot()

    /**
     * 返回默认总线中指定 scope 的只读诊断快照；scope 尚未创建时返回 `null`。
     */
    fun inspectScope(scopeName: String): FlowBusScopeSnapshot? {
        require(scopeName.isNotBlank()) { "scopeName must not be blank" }
        return installedBus?.inspectScope(scopeName)
    }

    /**
     * 返回指定名称对应的共享作用域。
     */
    fun scoped(scopeName: String): ScopedFlowBus = raw().scoped(scopeName)

    /**
     * 返回指定 [FlowBusOwner] 对应的共享作用域。
     */
    fun scoped(owner: FlowBusOwner): ScopedFlowBus = raw().scoped(owner)

    /**
     * [scoped] 的简短别名，保留给偏好 `scope(...)` 命名的调用方。
     */
    fun scope(scopeName: String): ScopedFlowBus = scoped(scopeName)

    /**
     * [scoped] 的简短别名，保留给偏好 `scope(...)` 命名的调用方。
     */
    fun scope(owner: FlowBusOwner): ScopedFlowBus = scoped(owner)

    /**
     * 打开一个需要显式关闭的作用域句柄。
     */
    fun openScope(scopeName: String): FlowBusScope = raw().openScope(scopeName)

    /**
     * 打开一个会跟随 [Job] 生命周期自动关闭的作用域句柄。
     */
    fun openScope(scopeName: String, closeWhen: Job): FlowBusScope = raw().openScope(scopeName, closeWhen)

    /**
     * 打开一个会跟随 [CoroutineScope] 生命周期自动关闭的作用域句柄。
     */
    fun openScope(scopeName: String, closeWhen: CoroutineScope): FlowBusScope = raw().openScope(scopeName, closeWhen)

    /**
     * 打开指定 [FlowBusOwner] 对应的显式作用域句柄。
     */
    fun openScope(owner: FlowBusOwner): FlowBusScope = raw().openScope(owner)

    /**
     * 打开指定 [FlowBusOwner] 对应、并跟随 [Job] 自动关闭的作用域句柄。
     */
    fun openScope(owner: FlowBusOwner, closeWhen: Job): FlowBusScope = raw().openScope(owner, closeWhen)

    /**
     * 打开指定 [FlowBusOwner] 对应、并跟随 [CoroutineScope] 自动关闭的作用域句柄。
     */
    fun openScope(owner: FlowBusOwner, closeWhen: CoroutineScope): FlowBusScope = raw().openScope(owner, closeWhen)

    /**
     * 移除指定名称对应 scope 的当前内部 store。
     */
    fun removeScope(scopeName: String) {
        raw().removeScope(scopeName)
    }

    /**
     * 删除指定 [FlowBusOwner] 对应的 scoped bus。
     */
    fun removeScope(owner: FlowBusOwner) {
        raw().removeScope(owner)
    }

    /**
     * 尝试发送指定 key 的普通事件。
     */
    fun <T : Any> post(key: EventKey<T>, value: T): Boolean {
        return raw().post(key = key, value = value)
    }

    /**
     * 尝试发送指定 key 的普通事件，并返回总线层面的诊断结果。
     */
    fun <T : Any> tryPostResult(key: EventKey<T>, value: T): FlowBusPostResult {
        return raw().tryPostResult(key = key, value = value)
    }

    /**
     * 挂起直到指定 key 的普通事件成功发送。
     */
    suspend fun <T : Any> emit(key: EventKey<T>, value: T) {
        raw().emit(key = key, value = value)
    }

    /**
     * 尝试发送指定 key 的粘性事件。
     */
    fun <T : Any> postSticky(key: EventKey<T>, value: T): Boolean {
        return raw().postSticky(key = key, value = value)
    }

    /**
     * 尝试发送指定 key 的粘性事件，并返回总线层面的诊断结果。
     */
    fun <T : Any> tryPostStickyResult(key: EventKey<T>, value: T): FlowBusPostResult {
        return raw().tryPostStickyResult(key = key, value = value)
    }

    /**
     * 挂起直到指定 key 的粘性事件成功发送。
     */
    suspend fun <T : Any> emitSticky(key: EventKey<T>, value: T) {
        raw().emitSticky(key = key, value = value)
    }

    /**
     * 返回指定 key 的普通事件流。
     */
    fun <T : Any> flow(key: EventKey<T>) = raw().flow(key)

    /**
     * 返回指定 key 的粘性事件流。
     */
    fun <T : Any> stickyFlow(key: EventKey<T>) = raw().stickyFlow(key)

    /**
     * 按顺序收集指定 key 的普通事件，并自动使用默认总线的错误处理配置。
     */
    suspend fun <T : Any> collect(
        key: EventKey<T>,
        dispatcher: CoroutineDispatcher? = null,
        onReceived: (T) -> Unit
    ) {
        raw().collect(key = key, dispatcher = dispatcher, onReceived = onReceived)
    }

    /**
     * 按顺序收集指定 key 的粘性事件，并自动使用默认总线的错误处理配置。
     */
    suspend fun <T : Any> collectSticky(
        key: EventKey<T>,
        dispatcher: CoroutineDispatcher? = null,
        onReceived: (T) -> Unit
    ) {
        raw().collectSticky(key = key, dispatcher = dispatcher, onReceived = onReceived)
    }

    /**
     * 从默认总线中移除指定 key 对应普通事件的当前 store 条目。
     */
    fun <T : Any> removeEvent(key: EventKey<T>) {
        raw().removeEvent(key)
    }

    /**
     * 清空指定 key 对应粘性事件的 replay 缓存。
     */
    fun <T : Any> clearSticky(key: EventKey<T>) {
        raw().clearSticky(key)
    }

    /**
     * 从默认总线中移除指定 key 对应粘性事件的当前 store 条目，并清空现有 replay 缓存。
     */
    fun <T : Any> removeSticky(key: EventKey<T>) {
        raw().removeSticky(key)
    }

    /**
     * 读取指定 key 对应粘性事件的最新 replay 值，并清空该 sticky replay 缓存。
     */
    fun <T : Any> consumeStickyLatest(key: EventKey<T>): T? {
        return raw().consumeStickyLatest(key)
    }

    /**
     * 尝试发送普通事件。
     */
    inline fun <reified T : Any> post(value: T, eventName: String = defaultEventName<T>()): Boolean {
        return raw().post<T>(value = value, eventName = eventName)
    }

    /**
     * 尝试发送普通事件，并返回总线层面的诊断结果。
     */
    inline fun <reified T : Any> tryPostResult(
        value: T,
        eventName: String = defaultEventName<T>()
    ): FlowBusPostResult {
        return raw().tryPostResult<T>(value = value, eventName = eventName)
    }

    /**
     * 挂起直到普通事件成功发送。
     */
    suspend inline fun <reified T : Any> emit(value: T, eventName: String = defaultEventName<T>()) {
        raw().emit<T>(value = value, eventName = eventName)
    }

    /**
     * 尝试发送粘性事件。
     */
    inline fun <reified T : Any> postSticky(value: T, eventName: String = defaultEventName<T>()): Boolean {
        return raw().postSticky<T>(value = value, eventName = eventName)
    }

    /**
     * 尝试发送粘性事件，并返回总线层面的诊断结果。
     */
    inline fun <reified T : Any> tryPostStickyResult(
        value: T,
        eventName: String = defaultEventName<T>()
    ): FlowBusPostResult {
        return raw().tryPostStickyResult<T>(value = value, eventName = eventName)
    }

    /**
     * 挂起直到粘性事件成功发送。
     */
    suspend inline fun <reified T : Any> emitSticky(value: T, eventName: String = defaultEventName<T>()) {
        raw().emitSticky<T>(value = value, eventName = eventName)
    }

    /**
     * 返回普通事件流。
     */
    inline fun <reified T : Any> flow(eventName: String = defaultEventName<T>()) = raw().flow<T>(eventName = eventName)

    /**
     * 返回粘性事件流。
     */
    inline fun <reified T : Any> stickyFlow(eventName: String = defaultEventName<T>()) =
        raw().stickyFlow<T>(eventName = eventName)

    /**
     * 按顺序收集普通事件，并自动使用默认总线的错误处理配置。
     */
    suspend inline fun <reified T : Any> collect(
        eventName: String = defaultEventName<T>(),
        dispatcher: CoroutineDispatcher? = null,
        noinline onReceived: (T) -> Unit
    ) {
        raw().collect<T>(eventName = eventName, dispatcher = dispatcher, onReceived = onReceived)
    }

    /**
     * 按顺序收集粘性事件，并自动使用默认总线的错误处理配置。
     */
    suspend inline fun <reified T : Any> collectSticky(
        eventName: String = defaultEventName<T>(),
        dispatcher: CoroutineDispatcher? = null,
        noinline onReceived: (T) -> Unit
    ) {
        raw().collectSticky<T>(eventName = eventName, dispatcher = dispatcher, onReceived = onReceived)
    }

    /**
     * 从默认总线中移除指定普通事件的当前 store 条目。
     */
    inline fun <reified T : Any> removeEvent(eventName: String = defaultEventName<T>()) {
        raw().removeEvent<T>(eventName = eventName)
    }

    /**
     * 清空指定粘性事件的 replay 缓存。
     */
    inline fun <reified T : Any> clearSticky(eventName: String = defaultEventName<T>()) {
        raw().clearSticky<T>(eventName = eventName)
    }

    /**
     * 从默认总线中移除指定粘性事件的当前 store 条目，并清空现有 replay 缓存。
     */
    inline fun <reified T : Any> removeSticky(eventName: String = defaultEventName<T>()) {
        raw().removeSticky<T>(eventName = eventName)
    }

    /**
     * 读取指定粘性事件的最新 replay 值，并清空该 sticky replay 缓存。
     */
    inline fun <reified T : Any> consumeStickyLatest(eventName: String = defaultEventName<T>()): T? {
        return raw().consumeStickyLatest<T>(eventName = eventName)
    }

    internal fun resetForTests() {
        synchronized(this) {
            installedBus = null
        }
    }

    private fun getOrCreateBus(): FlowBus {
        return installedBus ?: synchronized(this) {
            installedBus ?: FlowBus().also { installedBus = it }
        }
    }

    private fun emptySnapshot(): FlowBusSnapshot {
        return FlowBusSnapshot(
            config = FlowBusConfig().toSnapshot(),
            root = FlowBusScopeSnapshot(
                scopeName = null,
                events = emptyList()
            ),
            scopes = emptyList()
        )
    }
}
