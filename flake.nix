{
  description = "newm on Caulis: a Wayland compositor of an endless wall of windows, made for touchpads";

  inputs = {
    # The libraries newm opens while running. Safe to point at your own nixpkgs
    # (inputs.newm.inputs.nixpkgs.follows = "nixpkgs"): newm then uses your system's Mesa, ...
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
    # The Gradle newm is built with: exactly the one nix/deps.json (the list of Gradle's
    # downloads) was made with (nix/deps-gradle-version). Pinned to a revision on purpose; do NOT
    # make it follow your nixpkgs: another Gradle wants other files (the build stops and says so).
    # Moving it means making deps.json again: nix run .#update-deps
    nixpkgs-gradle.url = "github:nixos/nixpkgs/31ecbaca1bdefa45dffc409f0250bd8039ea0010";
    # JDK 27 and the list of C libraries Caulis opens while running.
    caulis = {
      url = "github:AristarkhKenebas/caulis";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    { self, nixpkgs, nixpkgs-gradle, caulis }:
    let
      # Wayland compositors run on Linux only.
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAll = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      # nix build / nix run: newm itself.
      packages = forAll (pkgs: {
        default = pkgs.callPackage ./nix/package.nix {
          # Not the Gradle of `pkgs` (which may be the user's nixpkgs): the pinned one.
          gradle = nixpkgs-gradle.legacyPackages.${pkgs.stdenv.hostPlatform.system}.gradle_9;
          jdk = caulis.packages.${pkgs.stdenv.hostPlatform.system}.jdk;
          libraries = caulis.lib.libraries pkgs;
        };
        newm = self.packages.${pkgs.stdenv.hostPlatform.system}.default;
      });

      # nix run .#update-deps (in newm's folder): makes nix/deps.json again with the pinned
      # Gradle and writes that Gradle's version to nix/deps-gradle-version.
      apps = forAll (pkgs: {
        update-deps = {
          type = "app";
          program = "${self.packages.${pkgs.stdenv.hostPlatform.system}.default.updateDeps}";
        };
      });

      # NixOS: programs.newm (installs newm, adds its session, /etc/newm/config.json).
      nixosModules.default = { pkgs, lib, ... }: {
        imports = [ ./nix/nixos-module.nix ];
        programs.newm.package = lib.mkDefault self.packages.${pkgs.stdenv.hostPlatform.system}.default;
      };
      nixosModules.newm = self.nixosModules.default;

      # home-manager: programs.newm (your configuration, declaratively).
      homeManagerModules.default = ./nix/hm-module.nix;
      homeManagerModules.newm = self.homeManagerModules.default;
    };
}
