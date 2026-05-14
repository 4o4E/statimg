# GitHub Card HTTP API

All endpoints return PNG images unless noted otherwise. The `theme` query parameter uses keys from `themes2d` in `config.yml`.

## Themes

```http
GET /github/themes
```

Returns the available 2D theme names as JSON.

## Stats Card

```http
GET /github/stats/{username}
```

Query parameters:

| Parameter | Description |
| --- | --- |
| `theme` | 2D theme name. Defaults to the built-in default theme. |
| `hide` | Comma-separated stat ids to hide, for example `issues,contribs`. |
| `show` | Comma-separated optional stat ids. Supported values: `reviews`, `prs_merged`, `prs_merged_percentage`, `discussions_started`, `discussions_answered`. |
| `show_icons` | `true` to render stat icons. |
| `hide_rank` | `true` to hide the rank badge. |
| `include_all_commits` | `true` to include all commits through GitHub Search API. |
| `commits_year` | Year used by the all-commits search query. |
| `exclude_repo` | Comma-separated repositories excluded from owned-star totals and all-commit search. |
| `rank_icon` | `default` or `percentile`. |
| `number_format` | `short` or `long`. |
| `number_precision` | Decimal precision for formatted numbers. |
| `custom_title` | Replaces the default card title. |

Example:

```http
GET /github/stats/octocat?theme=tokyonight&show_icons=true&show=reviews,prs_merged&rank_icon=percentile
```

## Top Languages Card

```http
GET /github/top-langs/{username}
```

Query parameters:

| Parameter | Description |
| --- | --- |
| `theme` | 2D theme name. |
| `layout` | `normal`, `compact`, `donut`, `donut-vertical`, `donut_vertical`, or `pie`. |
| `langs_count` | Maximum language count, clamped by renderer limits. |
| `hide` | Comma-separated language names to hide. |
| `hide_progress` | `true` to hide progress bars. For `normal`, this renders the compact legend-only layout. |
| `exclude_repo` | Comma-separated repositories excluded from language aggregation. |
| `size_weight` | Repository language size weight. Defaults to `1.0`. |
| `count_weight` | Repository language occurrence count weight. Defaults to `0.0`. |
| `stats_format` | `percentages` or `bytes`. |
| `custom_title` | Replaces the default card title. |

Example:

```http
GET /github/top-langs/octocat?layout=donut-vertical&langs_count=8&hide=html,css
```

## Repository Card

```http
GET /github/repo/{owner}/{repo}
```

Query parameters:

| Parameter | Description |
| --- | --- |
| `theme` | 2D theme name. |
| `show_owner` | `true` to render `owner/repo` as the title. |
| `description_lines_count` | Description line count, clamped to `1..3`. |

Example:

```http
GET /github/repo/octocat/Hello-World?show_owner=true&description_lines_count=2
```

## Notes

- Boolean parameters are enabled only by the literal value `true`, case-insensitive.
- Comma-separated parameters trim whitespace and ignore empty entries.
- Invalid enum values fall back to the default option for that endpoint.
- Request failures are returned as `400 Bad Request` with a plain text message.
