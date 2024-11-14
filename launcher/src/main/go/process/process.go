package process

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"syscall"

	"github.com/theckman/go-flock"
)

type LockablePidFile interface {
	AcquireLock() error
	ReleaseLock() error
	ClearPid() error
	WritePid(int) error
	Alive() bool
	ReadPid() (int, error)
}

type lockablePidFile struct {
	pidFile *os.File
	lock    *flock.Flock
}

var _ LockablePidFile = &lockablePidFile{}

func New(path string) (LockablePidFile, error) {
	if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
		if !os.IsExist(err) {
			return nil, fmt.Errorf("failed to create directories for the pid file '%s': %w", path, err)
		}
	}
	pidFile, err := os.OpenFile(path, os.O_RDWR|os.O_CREATE, 0600)
	if err != nil {
		return nil, fmt.Errorf("failed to open the pid file '%s': %w", path, err)
	}
	return &lockablePidFile{pidFile: pidFile, lock: flock.New(path)}, nil
}

func (p *lockablePidFile) AcquireLock() error {
	if err := p.lock.Lock(); err != nil {
		return fmt.Errorf("failed to acquire write lock on the pid file '%s': %w", p.pidFile.Name(), err)
	}
	return nil
}

func (p *lockablePidFile) ReleaseLock() error {
	if err := p.lock.Unlock(); err != nil {
		return fmt.Errorf("failed to release the lock on the pid file '%s': %w", p.pidFile.Name(), err)
	}
	return nil
}

func (p *lockablePidFile) ClearPid() error {
	name := p.pidFile.Name()
	if err := p.lock.Lock(); err != nil {
		return fmt.Errorf("failed to acquire write lock on the pid file '%s': %w", name, err)
	}
	if _, err := p.pidFile.Seek(0, 0); err != nil {
		return fmt.Errorf("failed to seek to the beginning of the pid file '%s': %w", name, err)
	}

	if err := p.pidFile.Truncate(0); err != nil {
		return fmt.Errorf("failed to truncate the pid file '%s': %w", name, err)
	}

	if err := p.lock.Unlock(); err != nil {
		return fmt.Errorf("failed to release the lock on the pid file '%s': %w", name, err)
	}
	return nil
}

func (p *lockablePidFile) WritePid(pid int) error {
	name := p.pidFile.Name()
	if err := p.lock.Lock(); err != nil {
		return fmt.Errorf("failed to acquire write lock on the pid file '%s': %w", name, err)
	}
	if err := p.ClearPid(); err != nil {
		return err
	}
	if _, err := p.pidFile.WriteString(fmt.Sprintf("%d\n", pid)); err != nil {
		return fmt.Errorf("failed to write to the pid file '%s': %w", name, err)
	}
	if err := p.pidFile.Sync(); err != nil {
		return fmt.Errorf("failed to sync the pid file '%s': %w", name, err)
	}
	if err := p.lock.Unlock(); err != nil {
		return fmt.Errorf("failed to release the lock on the pid file '%s': %w", name, err)
	}
	return nil
}

func (p *lockablePidFile) Alive() bool {
	pid, err := p.ReadPid()
	if err != nil {
		return false
	}

	process, err := os.FindProcess(pid)
	if err != nil {
		return false
	}

	if err := process.Signal(syscall.Signal(0)); err != nil {
		// If sig is 0, then no signal is sent, but error checking is still per‐
		// formed; this can be used to check for the existence of a process ID or
		// process group ID.
		return false
	}

	return true
}

func (p *lockablePidFile) ReadPid() (int, error) {
	name := p.pidFile.Name()
	if err := p.lock.RLock(); err != nil {
		return 0, fmt.Errorf("failed to acquire read lock on the pid file '%s': %w", name, err)
	}

	if _, err := p.pidFile.Seek(0, 0); err != nil {
		return 0, fmt.Errorf("failed to seek to the beginning of the pid file '%s': %w", name, err)
	}

	reader := bufio.NewReader(p.pidFile)
	line, _, err := reader.ReadLine()
	if err != nil {
		return 0, fmt.Errorf("failed reading from the pid file '%s': %w", name, err)
	}
	pid, err := strconv.Atoi(string(line))
	if err != nil {
		return 0, fmt.Errorf("failed parsing pid file '%s': %w", name, err)
	}
	if pid <= 0 {
		return 0, fmt.Errorf("pid file '%s' contains an invalid pid: %d", name, pid)
	}
	return pid, p.lock.Unlock()
}
