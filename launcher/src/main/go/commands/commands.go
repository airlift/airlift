package commands

import "strings"

type Command int

const (
	UNKNOWN Command = iota
	RUN
	START
	STOP
	RESTART
	KILL
	STATUS
	HELP
)

func ParseCommand(command string) Command {
	switch strings.ToLower(command) {
	case "run":
		return RUN
	case "start":
		return START
	case "stop":
		return STOP
	case "restart":
		return RESTART
	case "kill":
		return KILL
	case "status":
		return STATUS
	case "help":
		return HELP
	default:
		return UNKNOWN
	}
}

func (command Command) String() string {
	switch command {
	case UNKNOWN:
		return "unknown"
	case RUN:
		return "run"
	case START:
		return "start"
	case STOP:
		return "stop"
	case RESTART:
		return "restart"
	case KILL:
		return "kill"
	case STATUS:
		return "status"
	case HELP:
		return "help"
	default:
		return "unrecognized command"
	}
}

func ListAll() []string {
	return []string{
		RUN.String(),
		START.String(),
		STOP.String(),
		RESTART.String(),
		STATUS.String(),
		KILL.String(),
		HELP.String(),
	}
}

func IsCommand(command string) bool {
	return ParseCommand(command) > 0
}

func RewriteArgs(args []string) []string {
	ret := make([]string, len(args))

	for index, arg := range args {
		ret[index] = arg
		if arg == RESTART.String() {
			// if we invoked restart, we want to fork to a start command
			ret[index] = START.String()
		}
	}

	return ret
}
