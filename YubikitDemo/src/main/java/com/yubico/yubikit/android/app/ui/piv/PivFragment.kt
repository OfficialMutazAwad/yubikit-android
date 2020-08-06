package com.yubico.yubikit.android.app.ui.piv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.yubico.yubikit.android.app.R
import com.yubico.yubikit.android.app.ui.YubiKeyFragment
import com.yubico.yubikit.android.app.ui.getSecret
import com.yubico.yubikit.exceptions.ApduException
import com.yubico.yubikit.exceptions.ApplicationNotFound
import com.yubico.yubikit.piv.PivApplication
import com.yubico.yubikit.piv.Slot
import kotlinx.android.synthetic.main.fragment_piv.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bouncycastle.util.encoders.Hex

class PivFragment : YubiKeyFragment<PivApplication, PivViewModel>() {
    private val slots = listOf(PageProperties(Slot.AUTHENTICATION, R.string.piv_authentication),
            PageProperties(Slot.SIGNATURE, R.string.piv_signature),
            PageProperties(Slot.KEY_MANAGEMENT, R.string.piv_key_mgmt),
            PageProperties(Slot.CARD_AUTH, R.string.piv_card_auth))

    override val viewModel: PivViewModel by activityViewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_piv, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pager.adapter = PagerAdapter(this)
        TabLayoutMediator(tab_layout, pager) { tab, position ->
            tab.text = String.format("Slot %02X", slots[position].slot.value)
        }.attach()

        showCerts(false)

        viewModel.certificates.observe(viewLifecycleOwner, Observer {
            it?.run {
                showCerts(true)
            }
        })

        viewModel.result.observe(viewLifecycleOwner, Observer { result ->
            result.onFailure { e ->
                if (e is ApplicationNotFound) {
                    showCerts(false)
                }

                if (e is ApduException && e.statusCode == 0x6982.toShort()) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        viewModel.mgmtKey = Hex.decode(getSecret(requireContext(), R.string.piv_enter_mgmt_key, R.string.piv_mgmt_key_hint))
                    }
                }
            }
        })
    }

    private fun showCerts(visible: Boolean) {
        pager.visibility = if (visible) View.VISIBLE else View.GONE
        empty_view.visibility = if (visible) View.GONE else View.VISIBLE
        activity?.invalidateOptionsMenu()
    }

    inner class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = slots.size

        override fun createFragment(position: Int): Fragment {
            return PivCertificateFragment.newInstance(slots[position].slot, slots[position].nameResId)
        }
    }

    private data class PageProperties(val slot: Slot, val nameResId: Int)
}