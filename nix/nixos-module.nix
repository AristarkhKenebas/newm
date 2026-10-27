# NixOS: newm installed, in the login screen's sessions, with a configuration for everyone.
#
#   programs.newm = {
#     enable = true;
#     package = newm.packages.${pkgs.system}.default;   # or a newm of your own
#     settings = { view.corner_radius = 24; };          # /etc/newm/config.json (new users start from it)
#   };
{ config, lib, pkgs, ... }:

let
  cfg = config.programs.newm;
  json = pkgs.formats.json { };
  session = pkgs.writeTextFile {
    name = "newm-session";
    destination = "/share/wayland-sessions/newm.desktop";
    text = ''
      [Desktop Entry]
      Name=newm
      Comment=Windows on an endless wall, made for touchpads
      Exec=${lib.getExe cfg.package}
      Type=Application
      DesktopNames=newm
    '';
    passthru.providedSessions = [ "newm" ];
  };
in
{
  options.programs.newm = {
    enable = lib.mkEnableOption "newm, the Wayland compositor of an endless wall of windows";

    package = lib.mkOption {
      type = lib.types.package;
      description = "The newm package.";
    };

    settings = lib.mkOption {
      type = json.type;
      default = { };
      description = "The system's newm configuration (/etc/newm/config.json): new users' start, and theirs while they have none.";
    };
  };

  config = lib.mkIf cfg.enable {
    environment.systemPackages = [ cfg.package ];
    # The keyboard of services.xserver.xkb for newm too (it reads these when its configuration names none).
    environment.sessionVariables = lib.mkIf (config.services.xserver.xkb.layout or "" != "") {
      XKB_DEFAULT_LAYOUT = lib.mkDefault config.services.xserver.xkb.layout;
      XKB_DEFAULT_VARIANT = lib.mkDefault config.services.xserver.xkb.variant;
      XKB_DEFAULT_OPTIONS = lib.mkDefault config.services.xserver.xkb.options;
    };
    services.displayManager.sessionPackages = [ session ];
    environment.etc."newm/config.json" = lib.mkIf (cfg.settings != { }) {
      source = json.generate "newm-config.json" cfg.settings;
    };
    # The lock screen checks passwords with PAM's "login" service (there on NixOS); X11 programs.
    programs.xwayland.enable = lib.mkDefault true;
    security.polkit.enable = lib.mkDefault true;
    xdg.portal = {
      enable = lib.mkDefault true;
      extraPortals = lib.mkDefault [ pkgs.xdg-desktop-portal-gtk pkgs.xdg-desktop-portal-wlr ];
      # Files, dialogs, settings: gtk's; sharing the screen and screenshots: wlr's (newm has
      # wlr-screencopy), else screen sharing in browsers and meetings finds nothing.
      config.newm = {
        default = lib.mkDefault [ "gtk" ];
        "org.freedesktop.impl.portal.ScreenCast" = lib.mkDefault [ "wlr" ];
        "org.freedesktop.impl.portal.Screenshot" = lib.mkDefault [ "wlr" ];
      };
    };
  };
}
