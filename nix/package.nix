# newm, built from these sources with Gradle (offline: its dependencies are listed in
# deps.json, made with `nix run .#update-deps` in newm's folder).
{
  lib,
  stdenv,
  runCommand,
  writeShellScript,
  # Gradle: the very version deps.json was made with (nix/deps-gradle-version). The flake gives
  # its pinned nixpkgs-gradle's gradle_9, whatever nixpkgs the rest comes from: another
  # version resolves other files (e.g. the Kotlin plugin's variant for that Gradle), which
  # deps.json does not have.
  gradle,
  makeWrapper,
  # JDK 27 (Caulis' flake has one until nixpkgs does).
  jdk,
  # The C libraries Caulis looks up while running (Caulis' flake: lib.libraries pkgs).
  libraries,
  pam,
  fontconfig,
  freetype,
  # The bar's Skiko (libskiko-linux-x64.so) is linked against libGL (libglvnd, among Caulis'
  # libraries), libX11, libfontconfig and libstdc++: without them the bar cannot load.
  libx11,
}:

let
  depsGradleVersion =
    if builtins.pathExists ./deps-gradle-version then lib.fileContents ./deps-gradle-version else null;
  wrongGradle = ''
    newm: nix/deps.json (the list of Gradle's downloads) was made for Gradle ${toString depsGradleVersion},
    but this build got Gradle ${gradle.version}; it would look for files deps.json does not have
    (e.g. "Could not find kotlin-gradle-plugin-...-gradleNNN.jar").
    newm's flake builds with the Gradle of its own pinned input "nixpkgs-gradle":
      - do not make newm's nixpkgs-gradle follow your nixpkgs (remove
        inputs.newm.inputs.nixpkgs-gradle.follows); following "nixpkgs" itself is fine;
      - do not override newm's gradle;
      - or, if you mean to use this Gradle, make deps.json again in newm's folder:
        nix run .#update-deps'';
in
stdenv.mkDerivation (finalAttrs: {
  pname = "newm";
  version = "0.1.0";

  src = lib.cleanSourceWith {
    src = ../.;
    # Only what the build reads (not build/, not a heap dump lying around).
    filter = path: type:
      let name = baseNameOf path; in
      !(lib.hasSuffix ".hprof" name) && name != "build" && name != ".gradle" && name != "result" && name != ".kotlin";
  };

  nativeBuildInputs = [ gradle makeWrapper ];

  # Gradle's downloads, made once by the update script and checked by their hashes. Until
  # nix/deps.json is made (it is made by the maintainer once the Caulis this newm needs is
  # released), the build stops at once and says how to make it.
  mitmCache =
    let
      deps = gradle.fetchDeps {
        pkg = finalAttrs.finalPackage;
        data = ./deps.json;
      };
    in
    if builtins.pathExists ./deps.json then
      if depsGradleVersion != null && gradle.version != depsGradleVersion then throw wrongGradle else deps
    else
      runCommand "newm-deps-missing" { passthru = { inherit (deps) updateScript; }; } ''
        echo "newm: nix/deps.json (the list of Gradle's downloads) is not there yet." >&2
        echo "Make it in newm's folder: nix run .#update-deps" >&2
        echo "(It needs the Caulis version of gradle/libs.versions.toml released: on Maven Central or https://aristarkhkenebas.github.io/caulis/maven; see docs/en/install.md, \"How the Nix package is built\".)" >&2
        exit 1
      '';

  # The build compiles for Java 27 with that JDK (no toolchain downloads in Nix's sandbox).
  gradleFlags = [
    "-Dorg.gradle.java.installations.paths=${jdk}"
    "-Dorg.gradle.java.installations.auto-download=false"
    "-Dorg.gradle.java.installations.auto-detect=false"
    "-Dfile.encoding=utf-8"
    # gradle.properties turns the configuration cache on for everyday builds; Nix's
    # dependency fetcher (an init script) does not work with it.
    "-Dorg.gradle.configuration-cache=false"
    # Compiler warnings fail newm's own development builds, not a package build.
    "-Pnewm.strictWarnings=false"
  ];
  gradleBuildTask = "installDist";

  # nix run .#update-deps: makes nix/deps.json with this Gradle and records its version
  # (run in newm's folder, where flake.nix is).
  passthru.updateDeps = writeShellScript "newm-update-deps" ''
    set -euo pipefail
    if [ ! -f flake.nix ] || [ ! -d nix ]; then
      echo "newm: run this in newm's folder (where flake.nix is)." >&2
      exit 1
    fi
    ${(gradle.fetchDeps { pkg = finalAttrs.finalPackage; data = ./deps.json; }).updateScript}
    echo ${gradle.version} > nix/deps-gradle-version
    echo "newm: nix/deps.json made with Gradle ${gradle.version} (nix/deps-gradle-version)." >&2
  '';
  # (The tests start a compositor: not in the sandbox.)
  doCheck = false;

  installPhase = ''
    runHook preInstall
    mkdir -p $out/share/newm $out/bin
    cp -r build/install/newm/. $out/share/newm/
    # Its Java, and the libraries it opens by name (EGL, GBM, DRM, libinput, PAM, ...) or
    # Skiko's library needs (libGL, libX11, libfontconfig, libstdc++).
    # (newm's Java options — the G1 heap kept small, lib/compiler for config.kts's compiler
    # process — come with the start script; NEWM_GC_OPTS / JAVA_OPTS change them.)
    # (The start script's AOT cache lives in ~/.cache/newm/aot, not in the store; its key
    # holds this store path, so each build has its own though every file's time here is 1.)
    makeWrapper $out/share/newm/bin/newm $out/bin/newm \
      --set JAVA_HOME ${jdk} \
      --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath (libraries ++ [ pam fontconfig freetype libx11 stdenv.cc.cc.lib ])}
    runHook postInstall
  '';

  meta = {
    description = "Wayland compositor of an endless wall of windows, made for touchpads (newm on Caulis)";
    homepage = "https://github.com/AristarkhKenebas/newm";
    license = lib.licenses.mit;
    platforms = lib.platforms.linux;
    mainProgram = "newm";
  };
})
