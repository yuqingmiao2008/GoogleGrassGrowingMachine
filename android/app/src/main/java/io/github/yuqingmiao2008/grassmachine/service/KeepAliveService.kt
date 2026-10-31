package io.github.yuqingmiao2008.grassmachine.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 空实现的无障碍服务，唯一作用是让本应用获得更高的后台存活优先级。
 * 不读取、不记录、不上传任何界面内容或输入。
 */
class KeepAliveService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
