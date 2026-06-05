// swift-tools-version: 5.9
import PackageDescription

let package = Package(
  name: "exportedSpmMaplibre",
  platforms: [.iOS("12.0"), .macOS("10.13"), .tvOS("12.0"), .watchOS("4.0")],
  products: [
    .library(
      name: "exportedSpmMaplibre",
      type: .static,
      targets: ["exportedSpmMaplibre"])
  ],
  dependencies: [
    .package(
      url: "https://github.com/maplibre/maplibre-gl-native-distribution.git", exact: "6.17.1")
  ],
  targets: [
    .target(
      name: "exportedSpmMaplibre",
      dependencies: [
        .product(name: "MapLibre", package: "maplibre-gl-native-distribution")
      ],
      path: "Sources"

    )

  ]
)
