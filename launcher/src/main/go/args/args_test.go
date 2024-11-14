package args

import (
	"launcher/commands"
	"testing"

	"github.com/spf13/afero"
)

func TestParseCommandNameFirst(t *testing.T) {
	t.Setenv("JAVA_HOME", "")
	fs := setup(t)

	command, options, err := ParseOptions(fs, "/usr/local", []string{"run"})
	if err != nil {
		t.Fatalf("Unexpected error: %v", err)
	}
	if options == nil {
		t.Fatalf("Expected options to be not nill")
	}
	if command != commands.RUN {
		t.Fatalf("Expected command to be %s but got %s", commands.RUN, command)
	}
}

func TestParseCommandNameLast(t *testing.T) {
	t.Setenv("JAVA_HOME", "")
	fs := setup(t)

	command, _, err := ParseOptions(fs, "/usr/local", []string{"--etc-dir", "/etc/dir", "restart"})
	if err != nil {
		t.Fatalf("Unexpected error: %v", err)
	}
	expected := commands.RESTART
	if command != expected {
		t.Fatalf("Expected command to be %s but got %s", expected, command)
	}
}

func TestParseUnrecognizedCommand(t *testing.T) {
	t.Parallel()
	fs := setup(t)

	command, _, err := ParseOptions(fs, "/usr/local", []string{"--etc-dir", "/etc/dir", "something-is-wrong"})
	if err == nil {
		t.Fatalf("Expected to get error")
	}
	testErr(t, err, "unknown command: something-is-wrong")
	if command != commands.UNKNOWN {
		t.Fatalf("Expected command to be %s but got %s", commands.UNKNOWN, command)
	}
}

func TestParseUnrecognizedCommands(t *testing.T) {
	t.Parallel()
	fs := setup(t)
	command, _, err := ParseOptions(fs, "/usr/local", []string{"--etc-dir", "/etc/dir", "something-is-wrong", "this-is-too"})
	if err == nil {
		t.Fatalf("Expected to get error")
	}
	testErr(t, err, "unknown command: something-is-wrong this-is-too")
	if command != commands.UNKNOWN {
		t.Fatalf("Expected command to be %s but got %s", commands.UNKNOWN, command)
	}
}

func TestJavaHome(t *testing.T) {
	t.Setenv("JAVA_HOME", "")
	t.Run("explicitly provided JDK home", func(t *testing.T) {
		fs := setup(t)
		jvmDir := "/usr/lib/jvm"

		_, _, err := ParseOptions(fs, "/usr/local", []string{"--etc-dir", "/etc/dir", "--jvm-dir", jvmDir, "restart"})
		testErr(t, err, "JVM installation path is invalid: path "+jvmDir+" is invalid: open /usr/lib/jvm: file does not exist")

		must(t, fs.Mkdir(jvmDir, 0644))

		_, _, err = ParseOptions(fs, "/usr/local", []string{"--etc-dir", "/etc/dir", "--jvm-dir", jvmDir, "restart"})
		testErr(t, err, "JVM installation path is invalid: path "+jvmDir+" is invalid: open /usr/lib/jvm/bin/java: file does not exist")

		file, err := fs.Create(jvmDir + "/bin/java")
		must(t, err)
		must(t, fs.Chmod(file.Name(), 0100))

		_, args, err := ParseOptions(fs, "/usr/local", []string{"--etc-dir", "/etc/dir", "--jvm-dir", jvmDir, "restart"})
		if err != nil {
			t.Fatalf("Expected to pass")
		}

		if args.JvmDir != jvmDir {
			t.Fatalf("Expected args.JvmDir = %s but got %s", jvmDir, args.JvmDir)
		}
	})
}

func TestJavaHomeProvidedJdk(t *testing.T) {
	fs := setup(t)
	jvmDir := "/usr/lib/jvm-home"
	t.Setenv("JAVA_HOME", jvmDir)

	must(t, fs.Mkdir(jvmDir, 0644))
	file, err := fs.Create(jvmDir + "/bin/java")
	must(t, err)
	must(t, fs.Chmod(file.Name(), 0100))

	_, args, err := ParseOptions(fs, "/usr/local", []string{"--etc-dir", "/etc/dir", "restart"})
	if err != nil {
		t.Fatalf("Expected to pass but got: %v", err)
	}

	if args.JvmDir != jvmDir {
		t.Fatalf("Expected args.JvmDir = %s but got %s", jvmDir, args.JvmDir)
	}
}

func TestMissing(t *testing.T) {
	t.Setenv("JAVA_HOME", "")
	fs := afero.NewMemMapFs()
	var err error
	args := []string{"--etc-dir", "/etc/dir", "restart"}

	t.Run("etc directory", func(t *testing.T) {
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "etc directory is missing: could not find directory: /etc/dir")
	})

	t.Run("launcher config", func(t *testing.T) {
		must(t, fs.Mkdir("/etc/dir", 0644))
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "launcher config file is missing: could not find file: /usr/local/bin/launcher.properties")

		file, _ := fs.Create("/usr/local/bin/launcher.properties")
		file.WriteString("invalid\\u0")
		file.Close()
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "could not read launcher config file: malformed \\uxxxx encoding")
	})

	t.Run("node config", func(t *testing.T) {
		file, _ := fs.Create("/usr/local/bin/launcher.properties")
		file.WriteString("unknown=valid")
		file.Close()
		_, _, err = ParseOptions(fs, "/usr/local", args)

		file, _ = fs.Create("/etc/dir/node.properties")
		file.WriteString("invalid\\u0")
		file.Close()
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "could not read node config file: malformed \\uxxxx encoding")
	})

	t.Run("JVM config", func(t *testing.T) {
		file, _ := fs.Create("/etc/dir/node.properties")
		file.WriteString("node.data-dir=/data/dir")
		file.Close()
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "JVM config file is missing: could not find file: /etc/dir/jvm.config")
	})

	t.Run("config file", func(t *testing.T) {
		file, _ := fs.Create("/etc/dir/jvm.config")
		file.Close()
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "config file is missing: could not find file: /etc/dir/config.properties")
	})

	t.Run("data dir", func(t *testing.T) {
		file, _ := fs.Create("/etc/dir/config.properties")
		file.Close()
		_, _, err = ParseOptions(fs, "/usr/local", args)

		if err != nil {
			t.Fatalf("Expected /data/dir to be created")
		}
	})

	t.Run("pid file", func(t *testing.T) {
		file, _ := fs.Create("/data/dir/var/run/launcher.pid")
		file.Close()
		fs.Chmod("/data/dir/var/run/launcher.pid", 0400)
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "pid file is not writeable: path /data/dir/var/run/launcher.pid is invalid: permission denied")
	})

	t.Run("launcher log", func(t *testing.T) {
		fs.Chmod("/data/dir/var/run/launcher.pid", 0700)

		file, _ := fs.Create("/data/dir/var/log/launcher.log")
		file.Close()
		fs.Chmod("/data/dir/var/log/launcher.log", 0400)
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "launcher log is not writeable: path /data/dir/var/log/launcher.log is invalid: permission denied")
	})

	t.Run("launcher log", func(t *testing.T) {
		fs.Chmod("/data/dir/var/log/launcher.log", 0700)

		file, _ := fs.Create("/data/dir/var/log/server.log")
		file.Close()
		fs.Chmod("/data/dir/var/log/server.log", 0400)
		_, _, err = ParseOptions(fs, "/usr/local", args)
		testErr(t, err, "server log is not writeable: path /data/dir/var/log/server.log is invalid: permission denied")
	})
}

func TestInvalidProperties(t *testing.T) {
	t.Setenv("JAVA_HOME", "")
	fs := setup(t)
	_, _, err := ParseOptions(fs, "/usr/local", []string{"-JinvalidVM", "-DinvalidSystem", "restart"})

	testErr(t, err, "provided system properties are invalid: property is malformed: invalidSystem")
}

func testErr(t *testing.T, err error, expectedMessage string) {
	if err == nil {
		t.Fatalf("Expected to get error '%s' but got nil", expectedMessage)
	}
	if err.Error() != expectedMessage {
		t.Fatalf("Expected error message '%s' but got '%s'", expectedMessage, err.Error())
	}
}

func TestParseOptions(t *testing.T) {
	t.Setenv("JAVA_HOME", "")
	fs := setup(t)
	_, options, err := ParseOptions(fs, "/usr/local", []string{"--etc-dir", "/etc/dir", "restart"})

	if err != nil {
		t.Fatalf("Unexpected error: %v", err)
	}
	if options == nil {
		t.Fatalf("Expected parsed options but got nil")
	}
	expected := Options{
		Verbose:            false,
		InstallPath:        "/usr/local",
		EtcDir:             "/etc/dir",
		ConfigPath:         "/etc/dir/config.properties",
		DataDir:            "/data/dir",
		SecretsConfigPath:  "/etc/dir/secrets.toml",
		PidFile:            "/data/dir/var/run/launcher.pid",
		LauncherConfigPath: "/usr/local/bin/launcher.properties",
		NodeConfigPath:     "/etc/dir/node.properties",
		LauncherLog:        "/data/dir/var/log/launcher.log",
		ServerLog:          "/data/dir/var/log/server.log",
		LogLevelFile:       "",
	}
	expectedString := `Verbose         = false
InstallPath     = /usr/local
EtcDir          = /etc/dir
ConfigPath      = /etc/dir/config.properties
DataDir         = /data/dir
SecretsConfigPath = /etc/dir/secrets.toml
PidFile         = /data/dir/var/run/launcher.pid
LauncherConfigPath = /usr/local/bin/launcher.properties
NodeConfigPath  = /etc/dir/node.properties
LauncherLog     = /data/dir/var/log/launcher.log
ServerLog       = /data/dir/var/log/server.log
LogLevelFile    = 
JvmDir          = 
LauncherConfig  = map[]
NodeConfig      = map[node.data-dir:/data/dir]
JvmConfig       = []
JvmOptions      = []
SystemProperties = map[node.data-dir:/data/dir]
`
	if options.String() != expectedString {
		t.Fatalf("Expected options to be:\n%v\n\nbut got:\n%v\n", expectedString, options.String())
	}
	if options.Verbose != expected.Verbose {
		t.Fatalf("Expected options.Verbose to be %v but got %v", expected.Verbose, options.Verbose)
	}
	if options.InstallPath != expected.InstallPath {
		t.Fatalf("Expected options.InstallPath to be %v but got %v", expected.InstallPath, options.InstallPath)
	}
	if options.EtcDir != expected.EtcDir {
		t.Fatalf("Expected options.EtcDir to be %v but got %v", expected.EtcDir, options.EtcDir)
	}
	if options.ConfigPath != expected.ConfigPath {
		t.Fatalf("Expected options.ConfigPath to be %v but got %v", expected.ConfigPath, options.ConfigPath)
	}
	if options.DataDir != expected.DataDir {
		t.Fatalf("Expected options.DataDir to be %v but got %v", expected.DataDir, options.DataDir)
	}
	if options.PidFile != expected.PidFile {
		t.Fatalf("Expected options.PidFile to be %v but got %v", expected.PidFile, options.PidFile)
	}
	if options.LauncherConfigPath != expected.LauncherConfigPath {
		t.Fatalf("Expected options.LauncherConfigPath to be %v but got %v", expected.LauncherConfigPath, options.LauncherConfigPath)
	}
	if options.NodeConfigPath != expected.NodeConfigPath {
		t.Fatalf("Expected options.NodeConfigPath to be %v but got %v", expected.NodeConfigPath, options.NodeConfigPath)
	}
	if options.LauncherLog != expected.LauncherLog {
		t.Fatalf("Expected options.LauncherLog to be %v but got %v", expected.LauncherLog, options.LauncherLog)
	}
	if options.ServerLog != expected.ServerLog {
		t.Fatalf("Expected options.ServerLog to be %v but got %v", expected.ServerLog, options.ServerLog)
	}
	if options.LogLevelFile != expected.LogLevelFile {
		t.Fatalf("Expected options.LogLevelFile to be %v but got %v", expected.LogLevelFile, options.LogLevelFile)
	}
}

func TestTransformArgs(t *testing.T) {
	t.Parallel()
	t.Run("Simple array", func(t *testing.T) {
		expected := []string{
			"a1", "b1", "c1",
		}

		actual := transformArgs(expected)
		if !equalList(expected, actual) {
			t.Fatalf("Expected transformArgs to return %v, but got %v", expected, actual)
		}
	})

	t.Run("Simple array", func(t *testing.T) {
		expected := []string{
			"a1", "b1", "c1", "-D", "something", "-J", "something",
		}

		actual := transformArgs(expected)
		if !equalList(expected, actual) {
			t.Fatalf("Expected transformArgs to return %v, but got %v", expected, actual)
		}
	})

	t.Run("Actual replacement", func(t *testing.T) {
		input := []string{
			"a1", "-Dsomething", "-Jsomething", "b1", "c1",
		}

		actual := transformArgs(input)
		expected := []string{
			"a1", "-D", "something", "-J", "something", "b1", "c1",
		}
		if !equalList(expected, actual) {
			t.Fatalf("Expected transformArgs to return %v, but got %v", expected, actual)
		}
	})
}

func equalList[M1, M2 []K, K comparable](m1 M1, m2 M2) bool {
	if len(m1) != len(m2) {
		return false
	}
	for i, v1 := range m1 {
		if v2 := m2[i]; v1 != v2 {
			return false
		}
	}
	return true
}

func setup(t *testing.T) afero.Fs {
	fs := afero.NewMemMapFs()
	must(t, fs.Mkdir("/etc/dir", 0644))
	must(t, fs.Mkdir("/data/dir", 0644))
	must(t, fs.Mkdir("/usr/local/etc", 0644))
	must(t, fs.Mkdir("/usr/local/bin", 0644))

	names := []string{
		"/usr/local/bin/launcher.properties",
		"/usr/local/etc/node.properties",
		"/usr/local/etc/jvm.config",
		"/usr/local/etc/config.properties",
		"/usr/local/etc/secrets.toml",
		"/etc/dir/node.properties",
		"/etc/dir/jvm.config",
		"/etc/dir/config.properties",
		"/etc/dir/secrets.toml",
	}
	for _, name := range names {
		propFile, err := fs.Create(name)
		propFile = mustOne(t, propFile, err)

		if name == "/etc/dir/node.properties" {
			n, err := propFile.WriteString("node.data-dir=/data/dir")
			mustOne(t, n, err)
		}
		must(t, propFile.Close())
	}
	return fs
}

func must(t *testing.T, err error) {
	if err != nil {
		t.Fatal(err)
	}
}

func mustOne[T any](t *testing.T, obj T, err error) T {
	if err != nil {
		t.Fatal(err)
	}
	return obj
}
