package com.nexters.doctor24.todoc.ui.mask

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.Gravity
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.google.firebase.analytics.FirebaseAnalytics
import com.naver.maps.map.*
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.FusedLocationSource
import com.nexters.doctor24.todoc.R
import com.nexters.doctor24.todoc.analytics.*
import com.nexters.doctor24.todoc.base.BaseActivity
import com.nexters.doctor24.todoc.base.EventObserver
import com.nexters.doctor24.todoc.data.mask.response.StoreSale
import com.nexters.doctor24.todoc.databinding.ActivityMaskMapBinding
import com.nexters.doctor24.todoc.ui.map.MarkerUIData
import com.nexters.doctor24.todoc.ui.map.NaverMapFragment
import com.nexters.doctor24.todoc.ui.map.marker.MapMarkerManager
import com.nexters.doctor24.todoc.ui.map.popup.MaskIntroPopUpDialog
import com.nexters.doctor24.todoc.ui.mask.preview.PreviewMaskFragment
import com.nexters.doctor24.todoc.util.isCurrentMapDarkMode
import com.nexters.doctor24.todoc.util.selectStyle
import com.nexters.doctor24.todoc.util.toDp
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

internal class MaskActivity : BaseActivity<ActivityMaskMapBinding, MaskMapViewModel>(),
    OnMapReadyCallback, MapMarkerManager.MarkerClickListener, PreviewMaskFragment.PreviewListener {
    override val layoutResId: Int
        get() = R.layout.activity_mask_map
    override val viewModel: MaskMapViewModel by viewModel()

    companion object {
        private const val MAP_ZOOM_LEVEL_MAX = 17.0
        private const val MAP_ZOOM_LEVEL_MASK = 8.0
    }

    private lateinit var fusedLocationSource: FusedLocationSource
    private var locationState: LocationTrackingMode = LocationTrackingMode.Follow
    private lateinit var naverMap: NaverMap
    private lateinit var markerManager: MapMarkerManager

    private var isMarkerSelected = false

    private val analytics : FirebaseAnalytics by inject()

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        fusedLocationSource =
            FusedLocationSource(this, NaverMapFragment.LOCATION_PERMISSION_REQUEST_CODE)

        binding.apply {
            vm = viewModel
        }
        binding.maskMapView.apply {
            onCreate(savedInstanceState)
            getMapAsync(this@MaskActivity)
        }

        initView()
        initObserve()
    }

    override fun onMapReady(map: NaverMap) {
        naverMap = map
        naverMap.uiSettings.apply {
            isCompassEnabled = false
            isRotateGesturesEnabled = true
            isZoomControlEnabled = false
            isLocationButtonEnabled = false
            isTiltGesturesEnabled = false
            logoGravity = Gravity.TOP or Gravity.END
            setLogoMargin(0, 40.toDp, 24.toDp, 0)
        }
        naverMap.apply {
            locationSource = fusedLocationSource
            locationTrackingMode = locationState
            isNightModeEnabled = isCurrentMapDarkMode()
            setBackgroundResource(NaverMap.DEFAULT_BACKGROUND_DRWABLE_DARK)
            mapType = NaverMap.MapType.Navi
            minZoom = MAP_ZOOM_LEVEL_MASK
            maxZoom = MAP_ZOOM_LEVEL_MAX
        }

        markerManager = MapMarkerManager(this, naverMap).apply { listener = this@MaskActivity }

        naverMap.apply {
            addOnCameraIdleListener { showRefresh() }
            addOnLocationChangeListener { viewModel.onChangedMyLocation(it) }
            setOnMapClickListener { _, _ -> deSelectMarker() }
        }
    }

    override fun markerClick(marker: Marker) {
        deSelectMarker()
        marker.apply {
            isHideCollidedMarkers = true
            isHideCollidedSymbols = true
            isHideCollidedCaptions = true
        }

        val medicalData = Bundle().apply {
            (marker.tag as MarkerUIData)?.let {
                putParcelable(PreviewMaskFragment.KEY_MEDICAL, StoreSale(it.name,it.name,it.maskAddress,it.maskType,it.location.latitude.toFloat(),it.location.longitude.toFloat(),it.maskStock?:"",it.maskState.state))
            }
            naverMap.cameraPosition.target?.let { loc ->
                Timber.d("MapApps - $loc")
                putDoubleArray(
                    PreviewMaskFragment.KEY_MY_LOCATION,
                    doubleArrayOf(loc.latitude, loc.longitude)
                )
            }
        }
        PreviewMaskFragment().apply {
            setStyle(DialogFragment.STYLE_NORMAL, R.style.PreviewBottomSheetDialog)
            arguments = medicalData
            listener = this@MaskActivity
        }.show(supportFragmentManager, PreviewMaskFragment.TAG)

        markerManager.getMarkerItem(marker)?.run {
            if (markerManager.isEqualsSelectMarker(this)) return
            selectMarker(this)
        }
        moveMarkerBoundary(marker)
    }

    override fun markerBundleClick(marker: Marker) {
    }

    override fun onClosedPreview() {
        deSelectMarker()
    }

    private fun initView() {
        binding.buttonLocation.setOnClickListener {
            when (locationState) {
                LocationTrackingMode.Face -> {
                    locationState = LocationTrackingMode.NoFollow
                    binding.buttonLocation.setImageResource(R.drawable.ic_location_none)
                }
                LocationTrackingMode.NoFollow -> {
                    locationState = LocationTrackingMode.Follow
                    binding.buttonLocation.setImageResource(R.drawable.ic_location_local)
                }
                LocationTrackingMode.Follow -> {
                    locationState = LocationTrackingMode.Face
                    binding.buttonLocation.setImageResource(R.drawable.ic_location_follow)
                }
            }
            naverMap.locationTrackingMode = locationState
        }
    }

    private fun initObserve() {
        viewModel.currentMyLocation.observe(this, Observer {
            if (!binding.textBtnMask.isSelected) binding.textBtnMask.performClick()
        })

        viewModel.maskMarkerList.observe(this, EventObserver {
            if (it.isEmpty()) {
                val message = String.format(getString(R.string.medical_empty_mask))
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                markerManager.setMarker(arrayListOf())
                showRefresh()
            } else {
                markerManager.setMarker(it)
                showOnlyStock(viewModel.stockSwitchEvent.value ?: false)
                val cameraUpdate = CameraUpdate.fitBounds(markerManager.makeBounds(), 100).animate(
                    CameraAnimation.Easing
                )
                naverMap.apply {
                    moveCamera(cameraUpdate)
                }
            }
        })

        viewModel.errorData.observe(this, Observer {
            Timber.e("Exception: ${it.code}")
            Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
        })

        viewModel.refreshEvent.observe(this, Observer {
            if (::markerManager.isInitialized) markerManager.setMarker(arrayListOf())
            viewModel.reqMaskMarker(naverMap.cameraPosition.target)
            analytics.logEvent(MASK_REFRESH, null)
        })

        viewModel.closeEvent.observe(this, Observer {
            analytics.logEvent(MASK_MOSE_CLOSE, null)
            finish()
        })

        viewModel.maskSelected.observe(this, Observer {
            binding.textBtnMask.selectStyle(it)
            if (it) {
                viewModel.currentMyLocation.value?.let {
                    if (::markerManager.isInitialized) markerManager.setMarker(arrayListOf())
                    viewModel.reqMaskMarker(it ?: naverMap.cameraPosition.target)
                }
            }
        })

        viewModel.maskStockCount.observe(this, Observer {
            binding.textStockCount.text = getString(R.string.mask_stock_count, it)
        })

        viewModel.stockSwitchEvent.observe(this, Observer {
            binding.textStockSwitch.apply {
                isSelected = it
                text =
                    if (it) getString(R.string.mask_stock_on) else getString(R.string.mask_stock_off)
            }
            showOnlyStock(it)
            analytics.logEvent(MASK_STOCK_ON_OFF, Bundle().apply {
                putString(MASK_STOCK_ACTIVE_PARAM, it.toString())
            })
        })

        viewModel.showPopup.observe(this, Observer {
            if (it) MaskIntroPopUpDialog().show(supportFragmentManager, MaskIntroPopUpDialog.TAG)
        })

    }

    private fun showOnlyStock(switch: Boolean) {
        if (::markerManager.isInitialized && (viewModel.maskDisableList.value
                ?: arrayListOf()).isNotEmpty()
        ) {
            markerManager.onOffMarkerItems(
                viewModel.maskDisableList.value ?: arrayListOf(),
                !switch
            )
        }
    }

    private fun showRefresh() {
        if (!binding.btnRefresh.isVisible && !isMarkerSelected) {
            deSelectMarker()
            binding.btnRefresh.apply {
                isVisible = true
                startAnimation((AnimationUtils.loadAnimation(context, R.anim.anim_slide_in_down)))
            }
        }
    }

    private fun hideRefresh() {
        if (!markerManager.isMarkerEmpty()) binding.btnRefresh.isVisible = false
    }

    private fun moveMarkerBoundary(marker: Marker) {
        val cameraUpdate = CameraUpdate.scrollTo(marker.position).animate(CameraAnimation.Easing)
        naverMap.setContentPadding(0, 0, 0, 270.toDp)
        naverMap.moveCamera(cameraUpdate)
    }

    private fun selectMarker(markerItem: MarkerUIData?) {
        markerItem?.run {
            hideRefresh()
            isMarkerSelected = true
            markerManager.selectMarker(this)
        }
    }

    private fun deSelectMarker() {
        isMarkerSelected = false
        markerManager.deSelectMarker()
        naverMap.setContentPadding(0, 0, 0, 0)
    }

    override fun onStart() {
        super.onStart()
        binding.maskMapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.maskMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.maskMapView.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.maskMapView.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        binding.maskMapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.maskMapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.maskMapView.onLowMemory()
    }
}