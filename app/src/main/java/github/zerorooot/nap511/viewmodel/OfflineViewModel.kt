package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import github.zerorooot.nap511.service.OfflineService
import kotlinx.coroutines.launch

class OfflineViewModel(private val cookie: String) : ViewModel() {
    val offlineService : OfflineService by lazy {
        OfflineService.getInstance(cookie)
    }
    /**
     * savepath:
    wp_path_id:
    2496737817422976687
    url[0]:
    magnet%3A%3Fxt%3Durn%3Abtih%3ABF6694C940936E47BB739777DBF3AAFE1E82E37B%26dn%3Dipx178-fhd-mp4
    url[1]:
    magnet%3A%3Fxt%3Durn%3Abtih%3A9b79b062bcb58903792bf75d2a4af92fd11f9385%26dn%3DIPX-514
    uid:
    343652237
    sign:
    0002713b28654488a29f5ea2d776496d
    time:
    1675155957
     */
    fun addTask(list: List<String>) {
        viewModelScope.launch{
            val map = HashMap<String, String>()
            map["savepath"] = ""
            map["wp_path_id"] = "2496737817422976687"
            map["uid"]="343652237"
            map["sign"]="0002713b28654488a29f5ea2d776496d"
            map["time"] = (System.currentTimeMillis() / 1000).toString()
            list.forEachIndexed{ index, s ->
                map["url[$index]"] = s
            }
            offlineService.addTask(map)
        }

    }
}