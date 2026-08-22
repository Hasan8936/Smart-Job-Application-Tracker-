describe('Smart Job Tracker basic flow', () => {
    it('loads the homepage', () => {
        cy.visit('/', { timeout: 20000 })
        cy.contains('Login', { timeout: 10000 })
    })
})