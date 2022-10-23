{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs";
  };

  outputs = { self, nixpkgs }:
    let
      pkgs = nixpkgs.legacyPackages.x86_64-linux;
    in
    {
      devShell.x86_64-linux =
        pkgs.mkShell {
          shellHook = ''
            export JC_CLASSIC_HOME=/home/christoph/Documents/GitHub/oracle_javacard_sdks/jc305u3_kit/ 
            export JAVA_HOME="${pkgs.jdk8}/lib/openjdk"
          '';

          buildInputs = with pkgs; [
            maven
            jdk8
          ];
        };
    };
}
