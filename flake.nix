{
  description = "newm on Caulis: a Wayland compositor of an endless wall of windows, made for touchpads";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
    # JDK 27 and the list of C libraries Caulis opens while running.
    caulis = {
      url = "github:AristarkhKenebas/caulis";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    { self, nixpkgs, caulis }:
    let
      # Wayland compositors run on Linux only.
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAll = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      # nix build / nix run: newm itself.
      packages = forAll (pkgs: {
        default = pkgs.callPackage ./nix/package.nix {
          jdk = caulis.packages.${pkgs.stdenv.hostPlatform.system}.jdk;
          libraries = caulis.lib.libraries pkgs;
        };
        newm = self.packages.${pkgs.stdenv.hostPlatform.system}.default;
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
