package info.lwb.core.common.log

/**
 * Platform-independent logging abstraction.
 * Implementations should be cheap: message lambda is only evaluated by the implementation.
 */
interface AppLogger {
    /** Debug message. */
    fun d(ref: String, msg: () -> String)

    /** Informational message. */
    fun i(ref: String, msg: () -> String)

    /** Warning with optional [t] cause. */
    fun w(ref: String, msg: () -> String, t: Throwable? = null)

    /** Error with optional [t] cause. */
    fun e(ref: String, msg: () -> String, t: Throwable? = null)

    /** Verbose / very detailed diagnostic message. */
    fun v(ref: String, msg: () -> String)
}

/** No-op logger used until a platform implementation is installed. */
object NoopLogger : AppLogger {
    override fun d(ref: String, msg: () -> String) = Unit

    override fun i(ref: String, msg: () -> String) = Unit

    override fun w(ref: String, msg: () -> String, t: Throwable?) = Unit

    override fun e(ref: String, msg: () -> String, t: Throwable?) = Unit

    override fun v(ref: String, msg: () -> String) = Unit
}

/**
 * Global facade delegating to an installable [AppLogger]. Thread-safe swap via volatile field.
 */
object Logger : AppLogger {
    @Volatile private var delegate: AppLogger = NoopLogger

    /** Installs the concrete platform logger. */
    fun install(actual: AppLogger) {
        delegate = actual
    }

    override fun d(ref: String, msg: () -> String) = delegate.d(ref, msg)

    override fun i(ref: String, msg: () -> String) = delegate.i(ref, msg)

    override fun w(ref: String, msg: () -> String, t: Throwable?) = delegate.w(ref, msg, t)

    override fun e(ref: String, msg: () -> String, t: Throwable?) = delegate.e(ref, msg, t)

    override fun v(ref: String, msg: () -> String) = delegate.v(ref, msg)
}
