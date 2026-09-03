#!/usr/bin/env bash
# SPEC-2.3: Native Spring Boot Discovery & Legacy Engine Deprecation -- behavioral smoke test
#
# Mirrors validate-dap-jvm.sh / validate-refactor.sh: uses `vim.cmd('cquit 1')` on assertion failure
# so pass/fail is trustworthy, unlike scripts/validate.sh's `+lua assert(...)` pattern.

set -e

echo "=== TetraVim Spring Boot Discovery (SPEC-2.3) Smoke Test ==="

FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_DIR"' EXIT

mkdir -p "$FIXTURE_DIR/src/main/java/com/example"

cat > "$FIXTURE_DIR/pom.xml" <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>demo-app</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</project>
XML

cat > "$FIXTURE_DIR/src/main/java/com/example/DemoApplication.java" <<'JAVA'
package com.example;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
}
JAVA

cat > "$FIXTURE_DIR/src/main/java/com/example/HelloController.java" <<'JAVA'
package com.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {
    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }
}
JAVA

cat > "$FIXTURE_DIR/src/main/java/com/example/HelloService.java" <<'JAVA'
package com.example;

import org.springframework.stereotype.Service;

@Service
public class HelloService {
    public HelloService(HelloRepository repo) {}
}
JAVA

cat > "$FIXTURE_DIR/src/main/java/com/example/HelloRepository.java" <<'JAVA'
package com.example;

import org.springframework.stereotype.Repository;

@Repository
public interface HelloRepository {
}
JAVA

echo "[1/4] Static: spring.lua / spring-picker.lua / engine purge assertions..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  assert(not pcall(require, 'tetravim.util.engine'), 'tetravim.util.engine must be removed entirely')

  local s = require('tetravim.util.spring')
  assert(type(s.detect_root) == 'function', 'spring.detect_root missing')
  assert(type(s.find_main_class) == 'function', 'spring.find_main_class missing')
  assert(type(s.detect_app) == 'function', 'spring.detect_app missing')
  assert(type(s.build_dap_config) == 'function', 'spring.build_dap_config missing')
  assert(type(s.find_endpoints) == 'function', 'spring.find_endpoints missing')
  assert(type(s.find_beans) == 'function', 'spring.find_beans missing')

  local p = require('tetravim.util.spring-picker')
  assert(type(p.pick_endpoint) == 'function', 'spring-picker.pick_endpoint missing')
  assert(type(p.pick_bean) == 'function', 'spring-picker.pick_bean missing')
  assert(type(p.detect_app) == 'function', 'spring-picker.detect_app missing')

  local sb = require('tetravim.util.springboot-debug')
  assert(type(sb.setup_springboot_dap) == 'function', 'springboot-debug.setup_springboot_dap missing')
  assert(type(sb.launch_debug) == 'function', 'springboot-debug.launch_debug missing')
  assert(type(sb.dedup_insert) == 'function', 'springboot-debug.dedup_insert missing')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Module shapes and legacy engine purge verified')
end
" -c "qa!"

echo "[2/4] Behavioral: DAP configuration generation and idempotency across multiple buffer attaches..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local sb = require('tetravim.util.springboot-debug')

  -- Stub dap module
  local fake_dap = {
    configurations = {
      java = {},
    },
  }
  package.loaded['dap'] = fake_dap

  -- First buffer attach simulation
  sb.setup_springboot_dap(fixture)

  local deadline = vim.uv.now() + 5000
  while #fake_dap.configurations.java == 0 and vim.uv.now() < deadline do
    vim.wait(50)
  end

  assert(#fake_dap.configurations.java == 2, 'Expected 2 configurations (launch + attach), got ' .. tostring(#fake_dap.configurations.java))
  local launch_cfg = fake_dap.configurations.java[1]
  local attach_cfg = fake_dap.configurations.java[2]

  assert(launch_cfg.request == 'launch', 'First config must be launch')
  assert(launch_cfg.mainClass == 'com.example.DemoApplication', 'mainClass must be com.example.DemoApplication, got ' .. tostring(launch_cfg.mainClass))
  assert(launch_cfg.name == 'Spring Boot: demo-app', 'Launch config name mismatch: ' .. tostring(launch_cfg.name))
  assert(attach_cfg.request == 'attach', 'Second config must be attach')
  assert(attach_cfg.name == 'Spring Boot: demo-app (attach)', 'Attach config name mismatch: ' .. tostring(attach_cfg.name))

  -- Second buffer attach simulation in the same project -> must NOT duplicate
  sb.setup_springboot_dap(fixture)
  vim.wait(200, function() return false end, 50)

  assert(#fake_dap.configurations.java == 2, 'Second buffer attach must not add duplicate configs, total: ' .. tostring(#fake_dap.configurations.java))
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: DAP configuration generated and confirmed idempotent across buffers')
end
" -c "qa!"

echo "[3/4] Keymaps: <leader>jse, <leader>jsb, <leader>jsd maparg callbacks resolve and dispatch..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  vim.cmd('cd ' .. vim.fn.fnameescape(fixture))

  require('tetravim.util.jvm').setup_keymaps()

  local map_jse = vim.fn.maparg('<leader>jse', 'n', false, true)
  assert(type(map_jse) == 'table' and type(map_jse.callback) == 'function', '<leader>jse callback missing')

  local map_jsb = vim.fn.maparg('<leader>jsb', 'n', false, true)
  assert(type(map_jsb) == 'table' and type(map_jsb.callback) == 'function', '<leader>jsb callback missing')

  local map_jsd = vim.fn.maparg('<leader>jsd', 'n', false, true)
  assert(type(map_jsd) == 'table' and type(map_jsd.callback) == 'function', '<leader>jsd callback missing')

  -- Execute <leader>jsd and verify notification
  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level, opts)
    table.insert(notified, { msg = msg, level = level })
    orig_notify(msg, level, opts)
  end

  map_jsd.callback()

  local deadline = vim.uv.now() + 5000
  while #notified == 0 and vim.uv.now() < deadline do
    vim.wait(50)
  end
  vim.notify = orig_notify

  assert(#notified >= 1, 'Expected notification from <leader>jsd')
  local expected = 'Spring Boot: demo-app (maven) — com.example.DemoApplication'
  assert(notified[1].msg == expected, string.format('Expected \"%s\", got \"%s\"', expected, tostring(notified[1].msg)))
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Keymaps resolved and <leader>jsd correctly notified app details')
end
" -c "qa!"

echo "[4/4] Behavioral: <leader>jrd (launch_debug) registers config and triggers dap.continue..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  vim.cmd('cd ' .. vim.fn.fnameescape(fixture))

  local continue_called = false
  local fake_dap = {
    configurations = {
      java = {},
    },
    continue = function()
      continue_called = true
    end,
  }
  package.loaded['dap'] = fake_dap

  require('tetravim.util.jvm').setup_keymaps()
  local map_jrd = vim.fn.maparg('<leader>jrd', 'n', false, true)
  assert(type(map_jrd) == 'table' and type(map_jrd.callback) == 'function', '<leader>jrd callback missing')

  map_jrd.callback()

  local deadline = vim.uv.now() + 5000
  while not continue_called and vim.uv.now() < deadline do
    vim.wait(50)
  end

  assert(continue_called, 'dap.continue() must be called by launch_debug')
  assert(#fake_dap.configurations.java >= 1, 'Launch config must be registered in dap.configurations.java')
  assert(fake_dap.configurations.java[1].name == 'Spring Boot: demo-app', 'Launch config name mismatch')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: launch_debug successfully registered config and called dap.continue()')
end
" -c "qa!"

echo ""
echo "✔ Native Spring Boot Discovery (SPEC-2.3) smoke test PASSED."
