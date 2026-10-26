{
  description = "newm on Caulis: a Wayland compositor of an endless wall of windows, made for touchpads";

  outputs = { self }: {
    # NixOS: programs.newm (installs newm, adds its session, /etc/newm/config.json).
    nixosModules.default = import ./nix/nixos-module.nix;
    nixosModules.newm = self.nixosModules.default;

    # home-manager: programs.newm (your configuration, declaratively).
    homeManagerModules.default = import ./nix/hm-module.nix;
    homeManagerModules.newm = self.homeManagerModules.default;
  };
}
