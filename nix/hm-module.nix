# home-manager: newm configured declaratively.
#
#   programs.newm = {
#     enable = true;
#     settings = {
#       view = { corner_radius = 24; padding = 8; };
#       keyboard = { layouts = "us,ru"; options = "grp:alt_shift_toggle"; };
#       keys = { "L-Return" = "launch alacritty"; "L-h" = "move -1 0"; };
#       rules = [ { app_id = "mpv"; float = true; } ];
#       startup = [ "mako" ];
#     };
#   };
#
# settings are newm's config.json (see docs/en/other-formats.md and docs/en/settings.md);
# newm takes them on as soon as home-manager writes them. For a config.kts or config.lua of
# your own instead, set configKts or configLua (one of the three).
{ config, lib, pkgs, ... }:

let
  cfg = config.programs.newm;
  json = pkgs.formats.json { };
in
{
  options.programs.newm = {
    enable = lib.mkEnableOption "newm, the Wayland compositor of an endless wall of windows";

    package = lib.mkOption {
      type = lib.types.nullOr lib.types.package;
      default = null;
      description = "The newm package to put in your profile (null: newm is installed some other way, e.g. by the NixOS module).";
    };

    settings = lib.mkOption {
      type = json.type;
      default = { };
      description = "newm's configuration, as config.json (written to ~/.config/newm/config.json).";
    };

    configKts = lib.mkOption {
      type = lib.types.nullOr lib.types.lines;
      default = null;
      description = "A config.kts instead of settings.";
    };

    configLua = lib.mkOption {
      type = lib.types.nullOr lib.types.lines;
      default = null;
      description = "A config.lua instead of settings.";
    };
  };

  config = lib.mkIf cfg.enable {
    assertions = [
      {
        assertion = lib.length (lib.filter (x: x) [ (cfg.configKts != null) (cfg.configLua != null) (cfg.settings != { }) ]) <= 1;
        message = "programs.newm: set one of settings, configKts and configLua (newm reads config.kts first, then config.lua, then config.json).";
      }
    ];

    home.packages = lib.optional (cfg.package != null) cfg.package;

    xdg.configFile = lib.mkMerge [
      (lib.mkIf (cfg.configKts != null) { "newm/config.kts".text = cfg.configKts; })
      (lib.mkIf (cfg.configLua != null) { "newm/config.lua".text = cfg.configLua; })
      (lib.mkIf (cfg.configKts == null && cfg.configLua == null) {
        "newm/config.json".source = json.generate "newm-config.json" cfg.settings;
      })
    ];
  };
}
