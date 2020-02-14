package com.nexters.doctor24.todoc.ui.map.marker.group

import android.content.Context
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.databinding.library.baseAdapters.BR
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.nexters.doctor24.todoc.R
import com.nexters.doctor24.todoc.base.BaseDialogFragment
import com.nexters.doctor24.todoc.data.marker.response.OperatingDate
import com.nexters.doctor24.todoc.data.marker.response.ResMapMarker
import com.nexters.doctor24.todoc.databinding.GroupMarkerListDialogBinding
import com.nexters.doctor24.todoc.databinding.ItemListHospitalBinding
import com.nexters.doctor24.todoc.ext.dpToPixel
import com.nexters.doctor24.todoc.ui.map.NaverMapViewModel
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_list_hospital.*
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import timber.log.Timber


class GroupMarkerListDialog : BaseDialogFragment<GroupMarkerListDialogBinding>() {
    companion object {
        @JvmField
        val TAG: String = this::class.java.simpleName

        const val KEY_LIST: String = "KEY_LIST"
    }

    override val layoutResId: Int = R.layout.group_marker_list_dialog

    private val viewModel: NaverMapViewModel by sharedViewModel()
    private var groupData: ArrayList<ResMapMarker>? = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupData = arguments?.let{
            it.getParcelableArrayList<ResMapMarker>(KEY_LIST) as ArrayList<ResMapMarker>
        }
    }

    override fun onResume() {
        super.onResume()
        val metrics = DisplayMetrics()
        val wm = context?.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)
        val dialogWidth = (metrics.widthPixels - 16f.dpToPixel() * 2).toInt()
        dialog?.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.vm = viewModel
        initView()
        initObserve()
    }

    private fun initView() {
        val groupUIData = groupData?.map {
            GroupListHospitalUiData(
                id = it.id,
                isEmergency = it.emergency,
                isNight = it.nightTimeServe,
                placeName = it.placeName,
                todayHour = it.day
            )
        }
        binding.recyclerViewMarkerList.apply {
            adapter = GroupListAdapter().apply {
                submitList(groupUIData)
            }
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        }
    }

    private fun initObserve() {
        viewModel.dialogCloseEvent.observe(viewLifecycleOwner, Observer {
            dialogDismiss()
        })
    }

    override fun onDismiss() {

    }

    internal inner class GroupListAdapter : ListAdapter<GroupListHospitalUiData, GroupListViewHolder>(object : DiffUtil.ItemCallback<GroupListHospitalUiData>() {
        override fun areItemsTheSame(oldItem: GroupListHospitalUiData, newItem: GroupListHospitalUiData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GroupListHospitalUiData, newItem: GroupListHospitalUiData): Boolean {
            return oldItem.id == newItem.id
        }
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupListViewHolder {
            val binding: ItemListHospitalBinding = DataBindingUtil.inflate(LayoutInflater.from(parent.context), R.layout.item_list_hospital, parent, false)
            return GroupListViewHolder(binding)
        }

        override fun onBindViewHolder(holder: GroupListViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

    }

    inner class GroupListViewHolder(private val binding : ItemListHospitalBinding) : RecyclerView.ViewHolder(binding.root) {

        internal fun bind(item: GroupListHospitalUiData) {
            binding.item = item
            binding.executePendingBindings()
        }
    }

    internal data class GroupListHospitalUiData(
        val id: String,
        val isEmergency : Boolean = false,
        val isNight : Boolean = false,
        val isNormal : Boolean = !isEmergency && !isNight,
        val placeName : String = "",
        val todayHour : OperatingDate?
    )
}