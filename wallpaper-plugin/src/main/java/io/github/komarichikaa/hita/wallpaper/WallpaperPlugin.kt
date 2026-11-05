package io.github.komarichikaa.hita.wallpaper

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Observer
import com.stupidtree.hita.api.*
import java.util.concurrent.Executors

/** Settings only: all effects are applied by the host's existing native timetable. */
class WallpaperPlugin : ComposeUiPlugin {
    override val id = "io.github.komarichikaa.hita.wallpaper"
    override val displayName = "课表壁纸与样式"
    override val requiresApi = 6
    private var dispose: (() -> Unit)? = null
    override fun createView(context: Context, host: PluginUiHost): View {
        dispose?.invoke()
        val service = host.service(TimetableAppearanceService.KEY)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 48) }
        fun label(value: String) = TextView(context).also { it.text = value; it.textSize = 16f; it.setPadding(0, 16, 0, 12); column.addView(it) }
        fun button(title: String, action: () -> Unit) = Button(context).also { it.text = title; it.setOnClickListener { action() }; column.addView(it) }
        label("课表壁纸与样式")
        button("返回", host::close)
        if (service == null) { label("需要支持课表外观接口的 OH-MY-HITA（API 6）。"); return ScrollView(context).apply { addView(column) } }
        val worker = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        var closed = false
        var state = service.observe().value ?: TimetableAppearance()
        val status = label("修改后自动保存，返回原有课表查看效果。")
        var current: Context = context
        while (current is ContextWrapper && current !is ActivityResultRegistryOwner && current.baseContext !== current) current = current.baseContext
        val registry = (current as? ActivityResultRegistryOwner)?.activityResultRegistry
        val launcher: ActivityResultLauncher<Array<String>>? = registry?.register(
            "hita.wallpaper.pick", ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !closed) worker.execute {
                val result = runCatching { context.contentResolver.openInputStream(uri)?.use(service::importWallpaper) ?: error("无法打开图片") }
                main.post { if (!closed) status.text = result.fold({ "壁纸已保存" }, { "导入失败：${it.message}" }) }
            }
        }
        button("选择壁纸") { launcher?.launch(arrayOf("image/*")) }.isEnabled = launcher != null
        button("清除壁纸") { service.clearWallpaper() }
        val sliders = mutableListOf<Pair<SeekBar, (TimetableAppearance) -> Int>>()
        fun slider(title: String, max: Int, value: (TimetableAppearance) -> Int, change: (Int) -> TimetableAppearance) {
            val caption = label(title)
            val seek = SeekBar(context).apply { this.max = max }
            sliders += seek to value
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, user: Boolean) {
                    caption.text = "$title：$progress"
                    if (user) service.update(change(progress))
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
            column.addView(seek)
        }
        slider("课程卡片不透明度 %", 100, { it.cardOpacity }, { state.copy(cardOpacity = it) })
        slider("背景模糊度", 30, { it.blurRadius }, { state.copy(blurRadius = it) })
        slider("壁纸暗色遮罩 %", 80, { it.scrimOpacity }, { state.copy(scrimOpacity = it) })
        label("风格预设")
        button("清晰") { service.update(state.copy(cardOpacity = 95, blurRadius = 0, scrimOpacity = 15)) }
        button("柔光") { service.update(state.copy(cardOpacity = 70, blurRadius = 12, scrimOpacity = 20)) }
        button("磨砂") { service.update(state.copy(cardOpacity = 45, blurRadius = 25, scrimOpacity = 30)) }
        button("夜幕") { service.update(state.copy(cardOpacity = 75, blurRadius = 18, scrimOpacity = 50)) }
        button("恢复默认（含清除壁纸）") { service.reset() }
        label("壁纸仅保存在宿主私有目录，不上传。设置在关闭或卸载此设置插件后仍保留；可通过恢复默认清除。")
        val observer = Observer<TimetableAppearance> { settings ->
            state = settings
            sliders.forEach { (bar, get) -> bar.progress = get(settings) }
        }
        service.observe().observeForever(observer)
        dispose = {
            if (!closed) {
                closed = true; service.observe().removeObserver(observer); launcher?.unregister(); worker.shutdownNow()
            }
        }
        return ScrollView(context).apply {
            addView(column)
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { dispose?.invoke() }
            })
        }
    }
    override fun onDestroy() { dispose?.invoke(); dispose = null }
}
