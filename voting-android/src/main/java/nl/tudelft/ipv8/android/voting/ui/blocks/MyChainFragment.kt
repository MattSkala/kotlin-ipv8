package nl.tudelft.ipv8.android.voting.ui.blocks

class MyChainFragment : BlocksFragment() {
    override fun getPublicKey(): ByteArray {
        return getTrustChainCommunity().myPeer.publicKey.keyToBin()
    }
}
