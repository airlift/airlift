package process

import (
	"os"
	"testing"
)

func TestPid(t *testing.T) {
	t.Parallel()
	temp, err := os.CreateTemp("", "pidfile")
	if err != nil {
		t.Fatalf("failed to create temp pid file: %v", err)
	}
	err = temp.Close()
	if err != nil {
		t.Fatalf("failed to close temp pid file: %v", err)
	}
	defer os.Remove(temp.Name())

	p, err := New(temp.Name())
	if err != nil {
		t.Fatalf("failed to create new lockable pid file: %v", err)
	}
	if p == nil {
		t.Fatalf("new lockable pid file is nil")
	}
	err = p.AcquireLock()
	if err != nil {
		t.Fatalf("failed to lock the pid file: %v", err)
	}
	alive := p.Alive()
	if alive {
		t.Fatalf("expected no process to be alive before writing the pid")
	}
	expected := 99999999999
	err = p.WritePid(expected)
	if err != nil {
		t.Fatalf("failed to write pid to the pid file: %v", err)
	}
	actual, err := p.ReadPid()
	if err != nil {
		t.Fatalf("failed to read pid from the pid file: %v", err)
	}
	if actual != expected {
		t.Fatalf("expected to read pid %v but got %v", expected, actual)
	}
	alive = p.Alive()
	if alive {
		t.Fatalf("expected no process with pid %v to be alive", expected)
	}
	err = p.ReleaseLock()
	if err != nil {
		t.Fatalf("failed to release the pid file: %v", err)
	}
}
