package main

import (
	"errors"
	"fmt"
	"launcher/args"
	"launcher/commands"
	"launcher/directory"
	proc "launcher/process"
	"os"
	"path/filepath"
	"syscall"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/afero"
	"golang.org/x/sys/unix"
)

const LsbNotRunning = 3 // important for RPM uninstallation
var ErrProcessNotRunning = fmt.Errorf("Not running")

func run(process proc.LockablePidFile, options args.Options) error {
	if process.Alive() {
		pid, err := process.ReadPid()
		if err != nil {
			return err
		}
		if pid != os.Getpid() {
			return fmt.Errorf("already running as %d", pid)
		}
	}

	if err := options.CreateAppSymlinks(); err != nil {
		return err
	}

	if err := directory.MakeDirs(options.DataDir); err != nil {
		return fmt.Errorf("failed to create the data dir: %w", err)
	}

	if err := os.Chdir(options.DataDir); err != nil {
		return fmt.Errorf("failed to change working dir to the data dir: %w", err)
	}

	if err := process.WritePid(os.Getpid()); err != nil {
		return err
	}

	if err := redirectStdinToDevNull(); err != nil {
		return fmt.Errorf("failed to redirect the standard input to /dev/null: %w", err)
	}

	javaArgs, env, err := options.JavaExecution(false)
	if err != nil {
		return err
	}

	if err := syscall.Exec(javaArgs[0], javaArgs, env); err != nil {
		return fmt.Errorf("failed to exec Java app: %w", err)
	}

	return nil
}

func redirectStdinToDevNull() error {
	fd, err := os.Open(os.DevNull)
	if err != nil {
		return fmt.Errorf("failed to open /dev/null: %w", err)
	}
	if err := unix.Dup2(int(fd.Fd()), syscall.Stdin); err != nil {
		return fmt.Errorf("failed to duplicate the standard input file descriptor: %w", err)
	}

	if err := fd.Close(); err != nil {
		return fmt.Errorf("failed to close the standard input file descriptor: %w", err)
	}
	return nil
}

func start(process proc.LockablePidFile, options args.Options) error {
	child := false
	if process.Alive() {
		pid, err := process.ReadPid()
		if err != nil {
			return err
		}
		if pid == os.Getpid() {
			child = true
		} else {
			return fmt.Errorf("already running as %d", pid)
		}
	}

	if err := options.CreateAppSymlinks(); err != nil {
		return err
	}

	if err := directory.MakeDirs(filepath.Dir(options.LauncherLog)); err != nil {
		return fmt.Errorf("failed to create the launcher log dir: %w", err)
	}

	if err := directory.MakeDirs(options.DataDir); err != nil {
		return fmt.Errorf("failed to create the data dir: %w", err)
	}

	cwd, err := os.Getwd()
	if err != nil {
		return fmt.Errorf("failed to detect current working dir: %w", err)
	}

	if err := os.Chdir(options.DataDir); err != nil {
		return fmt.Errorf("failed to change working dir to the data dir: %w", err)
	}

	if !child {
		if err := process.AcquireLock(); err != nil {
			return err
		}
		pid, forkErr := proc.Fork(commands.RewriteArgs(os.Args), cwd)
		if forkErr != nil {
			return fmt.Errorf("failed to fork the current process: %w", forkErr)
		}
		// WritePid will release a lock
		if err := process.WritePid(pid); err != nil {
			return err
		}

		printInfo(fmt.Sprintf("started as %d", pid))
		return nil
	}

	log, err := directory.OpenAppend(options.LauncherLog)
	if err != nil {
		return fmt.Errorf("failed to open the launcher log for appending: %w", err)
	}

	if err := redirectOutput(log); err != nil {
		return fmt.Errorf("failed to redirect the standard output and error to the launcher log: %w", err)
	}

	javaArgs, env, err := options.JavaExecution(true)
	if err != nil {
		return err
	}

	if err = syscall.Exec(javaArgs[0], javaArgs, env); err != nil {
		return fmt.Errorf("failed to exec the Java app: %w", err)
	}

	return nil
}

// redirectOutput redirects stdout and stderr to a file descriptor
func redirectOutput(f *os.File) error {
	if err := unix.Dup2(int(f.Fd()), syscall.Stdout); err != nil {
		return fmt.Errorf("failed to duplicate the standard output file descriptor: %w", err)
	}

	if err := unix.Dup2(int(f.Fd()), syscall.Stderr); err != nil {
		return fmt.Errorf("failed to duplicate the standard error file descriptor: %w", err)
	}

	return nil
}

func stop(process proc.LockablePidFile) error {
	if err := terminate(process, syscall.SIGTERM, "Stopped"); err != nil {
		if errors.Is(err, ErrProcessNotRunning) {
			printInfo(err.Error())
		} else {
			return err
		}
	}
	return nil
}

func exitOk(err error) {
	printError(err.Error())
	os.Exit(0)
}

func kill(process proc.LockablePidFile) error {
	return terminate(process, syscall.SIGKILL, "Killed")
}

func terminate(process proc.LockablePidFile, signal syscall.Signal, message string) error {
	if !process.Alive() {
		return ErrProcessNotRunning
	}

	pid, err := process.ReadPid()
	if err != nil {
		return err
	}

	err = syscall.Kill(pid, signal)
	if err != nil && err != syscall.ESRCH {
		return fmt.Errorf("signaling pid %d failed due to: %w", pid, err)
	}

	for {
		if process.Alive() {
			time.Sleep(100 * time.Millisecond)
			continue
		}

		err := process.ClearPid()
		if err != nil {
			return err
		}
		break
	}

	printInfo(fmt.Sprintf("%s %d", message, pid))
	return nil
}

func restart(process proc.LockablePidFile, options args.Options) error {
	if err := stop(process); err != nil {
		if errors.Is(err, ErrProcessNotRunning) {
			printInfo(err.Error())
		} else {
			return err
		}
	}
	return start(process, options)
}

func status(process proc.LockablePidFile) error {
	if !process.Alive() {
		printInfo("Not running")
		os.Exit(LsbNotRunning)
	}
	pid, err := process.ReadPid()
	if err != nil {
		printInfo(fmt.Sprintf("Not running: %v", err))
		os.Exit(LsbNotRunning)
	}
	printInfo(fmt.Sprintf("Running as %d", pid))
	return nil
}

func printError(message string) {
	red := color.New(color.FgRed).SprintfFunc()
	yellow := color.New(color.FgYellow).SprintfFunc()

	if _, err := fmt.Fprintf(os.Stderr, "%s: %s\n", red("ERROR"), yellow(message)); err != nil {
		println(err.Error())
		os.Exit(3)
	}
}

func printInfo(message string) {
	green := color.New(color.FgGreen).SprintfFunc()
	cyan := color.New(color.FgCyan).SprintfFunc()

	if _, err := fmt.Fprintf(os.Stdout, "%s: %s\n", green("INFO"), cyan(message)); err != nil {
		exitError(1, err)
	}
}

func handleCommand(fs afero.Fs, command commands.Command, options args.Options) error {
	process, err := proc.New(options.PidFile)
	if err != nil {
		return err
	}

	switch command {
	case commands.RUN:
		return run(process, options)
	case commands.KILL:
		return kill(process)
	case commands.START:
		return start(process, options)
	case commands.STOP:
		return stop(process)
	case commands.RESTART:
		return restart(process, options)
	case commands.STATUS:
		return status(process)
	case commands.HELP:
		options.PrintUsage()
		return nil

	default:
		return fmt.Errorf("unrecognized command")
	}
}

func main() {
	currentFile, err := os.Executable()
	if err != nil {
		exitError(1, fmt.Errorf("failed to determine the current executable name: %w", err))
	}

	filesystem := afero.NewOsFs()
	basePath, err := directory.FindInstallPath(filesystem, currentFile)
	if err != nil {
		exitError(1, err)
	}

	command, options, err := args.ParseOptions(filesystem, basePath, os.Args[1:])

	if err != nil {
		exitError(1, err)
	}

	if err := handleCommand(filesystem, command, *options); err != nil {
		exitError(1, err)
	}
}

func exitError(exitCode int, err error) {
	printError(err.Error())
	os.Exit(exitCode)
}
