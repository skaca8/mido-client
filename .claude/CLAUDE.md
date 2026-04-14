# Claude Session Rules

## Session Initialization
**MANDATORY**: 세션 시작 즉시 `.claude/skills/claude_init.skill.yaml` 파일을 Read 도구로 읽어야 함.
이 파일의 instructions에 따라 모든 설정이 자동으로 로드됨.

## Critical Rules to Follow

### 1. Plan First, Execute After Approval

- Always present work plan before starting
- Get user approval before any file modifications
- User decides the direction and key decisions
- Never make assumptions - ask for confirmation

### 2. Use Spring-First Approach

- Check Spring built-in solutions before custom implementations
- Leverage Spring utilities (ObjectUtils, Collections, etc.)
- Avoid unnecessary complexity

### 3. No Guessing or Assumptions

- Don't assume features without checking documentation
- When uncertain, say "Let me check" instead of guessing
- Use WebFetch for Claude Code questions

### 4. Preserve Working Solutions

- Don't change working code without performance issues
- Ask "Why change what already works?" first
- Measure before optimizing

### 5. Question Detection Rule

- If message ends with "?", treat as question only
- Provide information without modifying files
- Distinguish between questions and work requests

### 6. Check Existing Content

- Always read files before editing/overwriting
- Preserve existing content when adding new sections
- Avoid data loss from careless overwrites

## Coding Principles

- Clean code with minimal intermediate variables
- Functional programming patterns where appropriate
- Spring utilities and best practices from start
- Time = Money - avoid wasteful iterations

### 7. Code Review and Design Decision Principles

- Prioritize code readability from **"developer without domain knowledge"** perspective
- Explicit code > Hidden logic (in business logic)
- Balance extensibility and maintainability
- Maintain consistent judgment criteria
- Realistic design considering team technical maturity

### 8. Architecture Decision Making

- Simple duplication removal vs entire system pattern consideration
- **"Can errors be traced without domain knowledge?"** criteria
- Prioritize debugging and problem-solving ease in production
- Provide explicit paths when tracing code flow
- Separate response patterns by external vendors (Alpensia, Sono, Hanwha, etc.)

### 9. AOP Usage Guidelines

- **Active use**: @Transactional, @Cacheable, logging, security (cross-cutting concerns)
- **Careful use**: Business validation, data transformation, response processing (domain logic)
- **Decision criteria**: "Will hiding this logic make problem-solving difficult?"
- **Reality consideration**: Documentation isn't updated, flow tracing becomes difficult when team members change

### 10. Analyze Existing Code First

- **Before creating new files**: Always check existing similar files for patterns
- **Check conventions**: Naming, annotations, structure, formatting style
- **Example cases**:
    - Repository creation: Check existing Repository interfaces for @Mapper usage
    - Mapper XML: Check for comment patterns like `<!--suppress SqlNoDataSourceInspection -->`
    - Entity classes: Check Lombok annotation patterns and field access levels
- **Consistency is key**: Match existing codebase style rather than imposing new patterns
- **When in doubt**: Ask "How are other similar files structured?"

### 11. Code Quality Standards

- **SonarLint Compliance**: All code must pass SonarLint analysis without violations
- **IntelliJ Warnings**: Code should not generate any IntelliJ IDEA warnings or errors
- **Quality Gates**:
    - Fix all code smells and vulnerabilities before completion
    - Ensure proper exception handling and resource management
    - Follow naming conventions and code formatting standards
- **Verification**: Always run quality checks before marking tasks as complete
- explan