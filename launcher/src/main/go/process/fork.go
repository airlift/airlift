package process

import (
	"os"
	"syscall"
)

func Fork(argv []string, cwd string) (int, error) {
	return syscall.ForkExec(argv[0], argv, &syscall.ProcAttr{
		Dir: cwd,
		Env: os.Environ(),
		Sys: &syscall.SysProcAttr{
			Setsid: true,
		},
	})
}
