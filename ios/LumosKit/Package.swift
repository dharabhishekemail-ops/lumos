// swift-tools-version: 5.9
// LumosKit — canonical shared cross-platform Lumos library.
// This package is the single source of truth for protocol, crypto, config,
// session, transport, and media on iOS. The Android side mirrors this
// architecture in its Gradle modules; both must produce byte-identical wire
// output per the Lumos Protocol & Interop Contract v1.0 §3.

import PackageDescription

let package = Package(
    name: "LumosKit",
    platforms: [.iOS(.v15), .macOS(.v12)],
    products: [
        .library(name: "LumosKit", targets: [
            "LumosCommon", "LumosProtocol", "LumosCryptoAPI",
            "LumosConfig", "LumosTransport", "LumosSession", "LumosMedia",
        ]),
    ],
    targets: [
        .target(name: "LumosCommon"),
        .target(name: "LumosProtocol",  dependencies: ["LumosCommon"]),
        .target(name: "LumosCryptoAPI", dependencies: ["LumosCommon"]),
        .target(name: "LumosConfig",    dependencies: ["LumosCommon", "LumosCryptoAPI"]),
        .target(name: "LumosTransport", dependencies: ["LumosCommon", "LumosProtocol"]),
        .target(name: "LumosSession",   dependencies: ["LumosCommon", "LumosProtocol", "LumosCryptoAPI", "LumosConfig", "LumosTransport"]),
        .target(name: "LumosMedia",     dependencies: ["LumosCommon", "LumosProtocol", "LumosCryptoAPI"]),

        .testTarget(name: "LumosProtocolTests", dependencies: ["LumosProtocol"], resources: [.copy("Fixtures")]),
        .testTarget(name: "LumosConfigTests",   dependencies: ["LumosConfig"]),
        .testTarget(name: "LumosCryptoTests",   dependencies: ["LumosCryptoAPI"]),
        .testTarget(name: "LumosSessionTests",  dependencies: ["LumosSession"]),
        .testTarget(name: "LumosMediaTests",    dependencies: ["LumosMedia"]),
    ]
)
