#
# Copyright 2010 Proofpoint, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
require 'rubygems'
require 'bundler'
require 'rubygems/dependency_installer'
require 'fileutils'

# This is required because Bundler memoizes its initial configuration,
# which is all fine and dandy for normal operation, but plays hell when
# you want to use it twice within a single ruby invocation for testing.
module Bundler
  class << self
    def reset
      @configured = nil
    end
  end
end

module Proofpoint
  module GemToJarPackager

    class Gemfile2Jar

      def self.run(gemfile_name, destination_jar_name, working_directory)
        g2d = Gemfile2Dir.new(working_directory)
        if (g2d.install_gems_from_gemfile(gemfile_name))
          d2j = Dir2Jar.new(g2d.bundle_path)
          d2j.sanitize_source
          d2j.create_jar_with_name(destination_jar_name)
          return true
        end
        return false
      end

    end


    class Gemfile2Dir

      def initialize(target_dir)
        @target = target_dir
      end

      def install_gems_from_gemfile(gemfile_name)
        gemfile = Pathname.new(gemfile_name).expand_path
        root = gemfile.dirname
        @lockfile = root.join("#{gemfile.basename}.lock")

        ENV['BUNDLE_GEMFILE'] = gemfile

        Bundler.settings[:path] = @target
        Bundler.settings[:disable_shared_gems] = 1

        begin
          Bundler.configure
          definition = Bundler::Definition.build(gemfile, @lockfile, nil)
          Bundler::Installer::install(root, definition, {})
        rescue Exception
          puts $!
          clean_working_directory
          return false
        end
        return true
      end

      def bundle_path
        "#{@target}/#{Gem.ruby_engine}/#{Gem::ConfigMap[:ruby_version]}"
      end

      def clean_working_directory
        Bundler.reset
        FileUtils.rm_rf(@target)
        FileUtils.rm_rf(@lockfile) unless @lockfile.nil?
      end
    end


    class Dir2Jar

      def initialize(source_dir)
        @source = source_dir
      end

      def create_jar_with_name(jar_name)
        jar_name = (jar_name + '.jar') unless jar_name.end_with?('.jar')
        system("jar cf #{jar_name} -C #{@source} .")
      end

      def sanitize_source
        FileUtils.rm_rf("#{@source}/bin")
        FileUtils.rm_rf("#{@source}/cache")
        FileUtils.rm_rf("#{@source}/doc")
      end

    end

  end
end
