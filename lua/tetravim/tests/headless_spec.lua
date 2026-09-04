-- lua/tetravim/tests/headless_spec.lua
-- Test that headless bootstrap sets global flag

describe('Headless bootstrap script', function()
  it('sets tetravim_headless global variable', function()
    -- Simulate sourcing the script
    local script_path = vim.fn.stdpath('config') .. '/scripts/headless-setup.sh'
    -- Execute the headless script
    os.execute(script_path)
    -- After execution, the environment variable should have set the global flag
    assert.is_true(vim.g.tetravim_headless == true)
  end)
end)
