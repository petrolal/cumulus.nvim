-- TetraVim AWS CloudFormation, SAM & Ansible Integration (Story 3.2, FR4, FR5)

return {
  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        yamlls = {
          settings = {
            yaml = {
              schemas = {
                ["https://s3.amazonaws.com/cfn-resource-specifications-us-east-1-prod/schemas/2.0.0/all-spec.json"] = "cloudformation/*.yaml",
                ["https://raw.githubusercontent.com/awslabs/goformation/v7.0.0/schema/sam.schema.json"] = "sam/*.yaml",
              },
              customTags = {
                "!Ref scalar",
                "!Sub scalar",
                "!Sub sequence",
                "!GetAtt scalar",
                "!GetAtt sequence",
                "!FindInMap sequence",
                "!Select sequence",
                "!Split sequence",
                "!Join sequence",
                "!ImportValue scalar",
                "!Base64 scalar",
                "!Cidr sequence",
                "!And sequence",
                "!Equals sequence",
                "!If sequence",
                "!Not sequence",
                "!Or sequence",
                "!Condition scalar",
              },
            },
          },
        },
        ansiblels = {
          filetypes = { "yaml.ansible", "ansible" },
        },
      },
    },
  },
}
