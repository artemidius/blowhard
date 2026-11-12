import SwiftUI

@main
struct BlowhardApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootView()
                .ignoresSafeArea(.keyboard)
        }
    }
}
