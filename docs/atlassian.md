# Atlassian's Confluence API

The Confluence has a lot of undocumented behavior,
with info scattered across blog posts and forum answers.

This page documents some of the relevant quirks to develop The Arqivist.

## Application descriptor

Confluence uses this JSON file to install apps.
It contains identifying information about the app itself,
and other pieces of configuration such as url endpoints and custom key-value pairs to save in each page's cache.

