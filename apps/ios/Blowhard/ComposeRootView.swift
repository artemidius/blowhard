import BlowhardIos
import SwiftUI

struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosAppControllerKt.BlowhardViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // State is owned by the Kotlin presenter; SwiftUI has no mirrored state to update.
    }
}
