{
  description = "Applied Cooking — Minecraft 1.12.2 (Forge) development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    flake-parts.inputs.nixpkgs-lib.follows = "nixpkgs";
  };

  outputs =
    inputs@{ flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];

      perSystem =
        { pkgs, ... }:
        let
          # Native libraries the Minecraft 1.12.2 LWJGL2 client needs at runtime.
          clientLibs = with pkgs; [
            libGL
            openal
            alsa-lib
            libpulseaudio
            libx11
            libxext
            libxcursor
            libxrandr
            libxxf86vm
            libxi
            libxinerama
            libxrender
            libxtst
          ];
        in
        {
          # Minecraft 1.12.2 / ForgeGradle 2.3 requires Java 8.
          devShells.default = pkgs.mkShell {
            name = "appliedcooking-1.12.2";

            packages = with pkgs; [
              jdk8
              git
              curl
              unzip
              which
            ] ++ clientLibs;

            JAVA_HOME = "${pkgs.jdk8}";
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath clientLibs;
          };
        };
    };
}
