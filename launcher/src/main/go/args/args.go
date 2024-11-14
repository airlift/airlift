package args

import (
	_ "embed"
	"flag"
	"fmt"
	"launcher/commands"
	"launcher/directory"
	"launcher/properties"
	"os"
	"path"
	"path/filepath"
	"reflect"
	"strings"

	"github.com/fatih/color"
	"github.com/spf13/afero"
)

var Version string

type Options struct {
	Verbose            bool
	InstallPath        string
	EtcDir             string
	ConfigPath         string
	DataDir            string
	SecretsConfigPath  string
	PidFile            string
	LauncherConfigPath string
	NodeConfigPath     string
	LauncherLog        string
	ServerLog          string
	LogLevelFile       string
	JvmDir             string

	LauncherConfig   map[string]string
	NodeConfig       map[string]string
	JvmConfig        []string // jvm.config
	JvmOptions       []string // -J passed options
	SystemProperties map[string]string

	PrintUsage func()
}

type parseFlags struct {
	*flag.FlagSet
	helpOpt             bool
	verboseOpt          bool
	etcDirOpt           string
	launcherConfigOpt   string
	nodeConfigOpt       string
	jvmConfigOpt        string
	configOpt           string
	secretsConfigOpt    string
	logLevelsFileOpt    string
	jvmDir              string
	dataDirOpt          string
	pidFileOpt          string
	launcherLogFileOpt  string
	serverLogFileOpt    string
	jvmOpt              ArrayFlags
	systemPropertiesOpt ArrayFlags
}

func (pf parseFlags) PrintUsage() {
	pf.Usage()
}

type ArrayFlags []string

func (i *ArrayFlags) String() string {
	return strings.Join(*i, " ")
}

func (i *ArrayFlags) Set(value string) error {
	*i = append(*i, strings.TrimSpace(value))
	return nil
}

func newParseFlags() *parseFlags {
	var flags = flag.NewFlagSet(directory.LauncherName, flag.ExitOnError)
	parseFlags := &parseFlags{
		FlagSet:             flags,
		jvmOpt:              ArrayFlags{},
		systemPropertiesOpt: ArrayFlags{},
	}

	parseFlags.Usage = func() {
		green := color.New(color.FgGreen).SprintfFunc()
		yellow := color.New(color.FgYellow).SprintfFunc()

		if Version != "" {
			_, _ = os.Stdout.WriteString(green("Airlift launcher version: %s\n", yellow(Version)))
		}

		_, _ = os.Stdout.WriteString(green("Usage: %s [options] command\n", yellow(os.Args[0])))
		_, _ = os.Stdout.WriteString(green("Commands available: %s\n", yellow(strings.Join(commands.ListAll(), ", "))))
		_, _ = os.Stdout.WriteString(green("Configuration options:\n"))
		parseFlags.PrintDefaults()
	}

	flags.BoolVar(&parseFlags.helpOpt, "help", false, "Displays help")
	flags.BoolVar(&parseFlags.verboseOpt, "verbose", false, "Verbose output")
	flags.BoolVar(&parseFlags.verboseOpt, "v", false, "Verbose output")
	flags.StringVar(&parseFlags.etcDirOpt, "etc-dir", "", "Defaults to INSTALL_PATH/etc")
	flags.StringVar(&parseFlags.launcherConfigOpt, "launcher-config", "", "Defaults to INSTALL_PATH/bin/launcher.properties")
	flags.StringVar(&parseFlags.nodeConfigOpt, "node-config", "", "Defaults to ETC_DIR/node.properties")
	flags.StringVar(&parseFlags.jvmConfigOpt, "jvm-config", "", "Defaults to ETC_DIR/jvm.config")
	flags.StringVar(&parseFlags.configOpt, "config", "", "Defaults to ETC_DIR/config.properties")
	flags.StringVar(&parseFlags.secretsConfigOpt, "secrets-config", "", "Defaults to ETC_DIR/secrets.toml")
	flags.StringVar(&parseFlags.logLevelsFileOpt, "log-levels-file", "", "Defaults to ETC_DIR/log.properties")
	flags.StringVar(&parseFlags.jvmDir, "jvm-dir", "", "JVM installation directory")
	flags.StringVar(&parseFlags.dataDirOpt, "data-dir", "", "Defaults to INSTALL_PATH")
	flags.StringVar(&parseFlags.pidFileOpt, "pid-file", "", "Defaults to DATA_DIR/var/run/launcher.pid")
	flags.StringVar(&parseFlags.launcherLogFileOpt, "launcher-log-file", "", "Defaults to DATA_DIR/var/log/launcher.log (only in daemon mode)")
	flags.StringVar(&parseFlags.serverLogFileOpt, "server-log-file", "", "Defaults to DATA_DIR/var/log/server.log (only in daemon mode)")
	flags.Var(&parseFlags.jvmOpt, "J", "Sets a JVM option. Can be used multiple times")
	flags.Var(&parseFlags.systemPropertiesOpt, "D", "Set a Java system property. Can be used multiple times")

	return parseFlags
}

func ParseOptions(fs afero.Fs, installPath string, args []string) (commands.Command, *Options, error) {
	// reset to defaults
	command, flags, err := ParseArgs(args)
	if err != nil {
		return commands.UNKNOWN, nil, err
	}

	if flags.helpOpt {
		flags.PrintUsage()
		os.Exit(0)
	}

	var options = Options{
		PrintUsage: flags.PrintUsage,
	}

	options.Verbose = flags.verboseOpt
	options.InstallPath = installPath
	options.JvmOptions = flags.jvmOpt
	options.NodeConfig = make(map[string]string)
	options.JvmConfig = []string{}
	options.SystemProperties = make(map[string]string)
	options.LauncherConfig = make(map[string]string)

	options.EtcDir, err = ResolvePath(fs, flags.etcDirOpt, filepath.Join(installPath, "etc"))
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("etc directory is missing: %w", err)
	}

	options.LauncherConfigPath, err = ResolveFile(fs, flags.launcherConfigOpt, filepath.Join(installPath, "bin/launcher.properties"))
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("launcher config file is missing: %w", err)
	}

	options.LauncherConfig, err = properties.LoadFile(fs, options.LauncherConfigPath)
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("could not read launcher config file: %w", err)
	}

	options.NodeConfigPath, err = ResolveFile(fs, flags.nodeConfigOpt)
	if err != nil && flags.nodeConfigOpt != "" {
		// If node.properties is explicitly passed
		return commands.UNKNOWN, nil, fmt.Errorf("node config file is missing: %w", err)
	}

	if nodePath, err := ResolveFile(fs, filepath.Join(options.EtcDir, "node.properties")); err == nil && options.NodeConfigPath == "" {
		options.NodeConfigPath = nodePath
	}

	if options.NodeConfigPath != "" {
		options.NodeConfig, err = properties.LoadFile(fs, options.NodeConfigPath)
		if err != nil {
			return commands.UNKNOWN, nil, fmt.Errorf("could not read node config file: %w", err)
		}
	}

	options.JvmConfig, err = ResolvePathAndReadFile(fs, flags.jvmConfigOpt, filepath.Join(options.EtcDir, "jvm.config"))
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("JVM config file is missing: %w", err)
	}

	options.ConfigPath, err = ResolveFile(fs, flags.configOpt, filepath.Join(options.EtcDir, "config.properties"))
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("config file is missing: %w", err)
	}

	options.SecretsConfigPath, err = ResolveFile(fs, flags.secretsConfigOpt, filepath.Join(options.EtcDir, "secrets.toml"))

	options.LogLevelFile, err = ResolveFile(fs, flags.logLevelsFileOpt, filepath.Join(options.EtcDir, "log.properties"))
	if flags.logLevelsFileOpt != "" && err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("log levels file is missing: %w", err)
	}

	if flags.jvmDir != "" || os.Getenv("JAVA_HOME") != "" {
		options.JvmDir, err = Resolve(fs, directory.VerifyJvmInstallation, flags.jvmDir, os.Getenv("JAVA_HOME"))
		if err != nil {
			return commands.UNKNOWN, nil, fmt.Errorf("JVM installation path is invalid: %w", err)
		}
	}

	options.DataDir, err = Resolve(fs, directory.EnsureWritable, flags.dataDirOpt, options.NodeConfig["node.data-dir"], installPath)
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("data dir is invalid: %w", err)
	}

	options.PidFile, err = Resolve(fs, directory.EnsureWritable, flags.pidFileOpt, filepath.Join(options.DataDir, "var/run/launcher.pid"))
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("pid file is not writeable: %w", err)
	}

	options.LauncherLog, err = Resolve(fs, directory.EnsureWritable, flags.launcherLogFileOpt, filepath.Join(options.DataDir, "var/log/launcher.log"))
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("launcher log is not writeable: %w", err)
	}

	options.ServerLog, err = Resolve(fs, directory.EnsureWritable, flags.serverLogFileOpt, filepath.Join(options.DataDir, "var/log/server.log"))
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("server log is not writeable: %w", err)
	}

	options.SystemProperties, err = properties.Parse(flags.systemPropertiesOpt)
	if err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("provided system properties are invalid: %w", err)
	}

	// Copy node.config properties into system properties
	for key, value := range options.NodeConfig {
		options.SystemProperties[key] = value
	}

	if options.Verbose {
		println(options.String())
	}

	return command, &options, nil
}

func ParseArgs(args []string) (commands.Command, *parseFlags, error) {
	parseFlags := newParseFlags()

	if len(args) == 0 {
		parseFlags.PrintUsage()
		os.Exit(1)
	}

	args = transformArgs(args)

	if commands.IsCommand(args[0]) {
		if err := parseFlags.Parse(args[1:]); err != nil {
			return commands.UNKNOWN, nil, fmt.Errorf("failed to parse flags: %w", err)
		}
		return commands.ParseCommand(args[0]), parseFlags, nil
	}

	if err := parseFlags.Parse(args); err != nil {
		return commands.UNKNOWN, nil, fmt.Errorf("failed to parse flags: %w", err)
	}
	if parseFlags.helpOpt {
		return commands.HELP, parseFlags, nil
	}

	if parseFlags.NArg() == 1 {
		if commands.IsCommand(parseFlags.Arg(0)) {
			return commands.ParseCommand(parseFlags.Arg(0)), parseFlags, nil
		}
		return commands.UNKNOWN, parseFlags, fmt.Errorf("unknown command: %s", parseFlags.Arg(0))
	}

	return commands.UNKNOWN, parseFlags, fmt.Errorf("unknown command: %s", strings.Join(parseFlags.Args(), " "))
}

// transformArgs changes -Dx into -D x so the flag library can parse it correctly
func transformArgs(args []string) []string {
	ret := make([]string, 0)
	for i := range args {
		if len(args[i]) > 2 && (strings.HasPrefix(args[i], "-D") || strings.HasPrefix(args[i], "-J")) {
			flag := args[i][:2]
			value := args[i][2:]
			ret = append(ret, flag, value)
		} else {
			ret = append(ret, args[i])
		}
	}
	return ret
}

// CreateAppSymlinks symlinks the 'etc', 'plugin' and 'secrets-plugin' directories into the data directory.
// This is needed to support programs that reference 'etc/xyz' from within
// their config files: log.levels-file=etc/log.properties
func (options *Options) CreateAppSymlinks() error {
	if options.EtcDir != path.Join(options.DataDir, "etc") {
		err := directory.CreateSymlink(options.EtcDir, path.Join(options.DataDir, "etc"))
		if err != nil {
			return fmt.Errorf("failed to symlink the etc dir inside the data dir: %w", err)
		}
	}
	if options.InstallPath != options.DataDir {
		err := directory.CreateSymlink(path.Join(options.InstallPath, "plugin"), path.Join(options.DataDir, "plugin"))
		if err != nil {
			return fmt.Errorf("failed to symlink the install dir inside the data dir: %w", err)
		}
		secretsPluginErr := directory.CreateSymlink(path.Join(options.InstallPath, "secrets-plugin"), path.Join(options.DataDir, "secrets-plugin"))
		if secretsPluginErr != nil {
			return fmt.Errorf("failed to symlink secrets-plugin from the install dir inside the data dir: %w\"", err)
		}
	}
	return nil
}

func (options *Options) String() string {
	v := reflect.ValueOf(*options)
	var builder strings.Builder
	for i := 0; i < v.NumField(); i++ {
		field := v.Type().Field(i)
		if !strings.Contains(v.Type().Field(i).Type.String(), "func()") {
			builder.WriteString(fmt.Sprintf("%-15s = %v\n", field.Name, v.Field(i).Interface()))
		}
	}
	return builder.String()
}
