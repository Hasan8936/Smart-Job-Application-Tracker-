describe('Smart Job Tracker basic flow', () => {
    it('loads the homepage', () => {
        cy.visit('/', { timeout: 20000 })
        cy.contains('Sign in', { timeout: 10000 })
    })
})