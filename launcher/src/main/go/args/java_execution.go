package args

import (
	"fmt"
	"launcher/directory"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
)

func getArchSpecificDirectory() string {
	return fmt.Sprintf("%s-%s", runtime.GOOS, runtime.GOARCH)
}

func (options *Options) JavaExecution(daemonize bool) ([]string, []string, error) {
	var javaBin = "java"

	if options.JvmDir == "" {
		var err error
		javaBin, err = exec.LookPath("java")
		if err != nil {
			return nil, nil, fmt.Errorf("java is not installed")
		}
	} else {
		javaBin = filepath.Join(options.JvmDir, "bin/java")
	}

	properties := make(map[string]string)
	for k, v := range options.SystemProperties {
		properties[k] = v
	}

	if directory.Exists(options.LogLevelFile) {
		properties["log.levels-file"] = options.LogLevelFile
	}

	if daemonize {
		properties["log.output-file"] = options.ServerLog
		properties["log.enable-console"] = "false"
	}

	mainClass, ok := options.LauncherConfig["main-class"]
	if !ok {
		return nil, nil, fmt.Errorf("launcher config is missing 'main-class' property")
	}

	properties["config"] = options.ConfigPath

	if options.SecretsConfigPath != "" {
		properties["secretsConfig"] = options.SecretsConfigPath
	}

	var systemProperties []string
	for k, v := range properties {
		systemProperties = append(systemProperties, fmt.Sprintf("-D%s=%s", k, v))
	}

	classpath := filepath.Join(options.InstallPath, "lib", "*")

	command := []string{javaBin, "-cp", classpath}
	command = append(command, options.JvmConfig...)
	if len(options.JvmOptions) != 0 {
		command = append(command, strings.Join(options.JvmOptions, " "))
	}
	system := getArchSpecificDirectory()
	agentName := options.LauncherConfig["agent-name"]
	if agentName != "" {
		path := filepath.Join(options.InstallPath, "bin", system, agentName)
		if options.Verbose {
			fmt.Println("Agent path: " + path)
		}
		if !directory.Exists(path) {
			return nil, nil, fmt.Errorf("agent does not exist at location %s", path)
		}
		command = append(command, "-agentpath:"+path)
	}

	command = append(command, systemProperties...)
	command = append(command, mainClass)

	if options.Verbose {
		fmt.Println(strings.Join(command, " "))
	}

	env := make(map[string]string)
	for _, e := range os.Environ() {
		pair := strings.SplitN(e, "=", 2)
		env[pair[0]] = pair[1]
	}

	processName := options.LauncherConfig["process-name"]
	if processName != "" {
		shim := filepath.Join(options.InstallPath, "bin", system, "libprocname.so")
		if options.Verbose {
			fmt.Println("Procname shim: " + shim)
		}
		if directory.Exists(shim) {
			env["LD_PRELOAD"] = fmt.Sprintf("%s:%s", env["LD_PRELOAD"], shim)
			env["PROCNAME"] = processName
		} else {
			fmt.Printf("procname shim does not exist at location %s\n", shim)
		}
	}

	var envOut []string
	for k, v := range env {
		envOut = append(envOut, fmt.Sprintf("%s=%s", k, v))
	}
	if options.Verbose {
		fmt.Println("Environment: " + strings.Join(envOut, " "))
	}

	return command, envOut, nil
}
