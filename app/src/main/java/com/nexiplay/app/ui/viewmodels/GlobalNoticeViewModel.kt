package com.nexiplay.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Notice
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GlobalNoticeViewModel : ViewModel() {
    private val _notices = MutableStateFlow<List<Notice>>(emptyList())
    val notices = _notices.asStateFlow()

    private val _dismissedNoticeIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedNoticeIds = _dismissedNoticeIds.asStateFlow()

    init {
        fetchNotices()
    }

    fun fetchNotices() {
        viewModelScope.launch {
            try {
                val fetchedNotices = SupabaseClient.main.from("notices").select {
                    filter {
                        eq("is_active", true)
                        isIn("platform", listOf("app", "both"))
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeList<Notice>()
                
                _notices.value = fetchedNotices
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissNotice(noticeId: String) {
        _dismissedNoticeIds.value = _dismissedNoticeIds.value + noticeId
    }
}
